package ru.vehxi.taskflow.tasks.service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.vehxi.taskflow.common.web.OperationException;
import ru.vehxi.taskflow.common.web.PageResponse;
import ru.vehxi.taskflow.projects.domain.Project;
import ru.vehxi.taskflow.projects.domain.ProjectMembershipId;
import ru.vehxi.taskflow.projects.repository.ProjectMembershipRepository;
import ru.vehxi.taskflow.projects.repository.ProjectRepository;
import ru.vehxi.taskflow.references.domain.TaskPriority;
import ru.vehxi.taskflow.references.domain.TaskType;
import ru.vehxi.taskflow.references.repository.TaskPriorityRepository;
import ru.vehxi.taskflow.references.repository.TaskTypeRepository;
import ru.vehxi.taskflow.tasks.domain.Task;
import ru.vehxi.taskflow.tasks.domain.Comment;
import ru.vehxi.taskflow.tasks.domain.TimeEntry;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;
import ru.vehxi.taskflow.tasks.repository.TaskRepository;
import ru.vehxi.taskflow.tasks.repository.CommentRepository;
import ru.vehxi.taskflow.tasks.repository.TimeEntryRepository;
import ru.vehxi.taskflow.tasks.web.TaskRequests;
import ru.vehxi.taskflow.tasks.web.TaskHistoryViews;
import ru.vehxi.taskflow.tasks.web.TaskViews;
import ru.vehxi.taskflow.users.domain.RoleCode;
import ru.vehxi.taskflow.users.domain.User;
import ru.vehxi.taskflow.users.repository.UserRepository;

/** Authorizes, searches and saves tasks. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TaskService {
    private static final Sort HISTORY_ORDER = Sort.by("createdAt", "id");
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final UserRepository users;
    private final TaskTypeRepository types;
    private final TaskPriorityRepository priorities;
    private final CommentRepository comments;
    private final TimeEntryRepository timeEntries;
    private final ZoneId zone;

    /** Creates task operations.
     * @param tasks task storage
     * @param projects project storage
     * @param memberships project membership storage
     * @param users account storage
     * @param types type storage
     * @param priorities priority storage
     * @param comments comment storage
     * @param timeEntries work log storage
     * @param timeZone zone for calendar business rules */
    public TaskService(TaskRepository tasks, ProjectRepository projects, ProjectMembershipRepository memberships,
            UserRepository users, TaskTypeRepository types, TaskPriorityRepository priorities,
            CommentRepository comments, TimeEntryRepository timeEntries, @Value("${app.time-zone}") String timeZone) {
        this.tasks = tasks;
        this.projects = projects;
        this.memberships = memberships;
        this.users = users;
        this.types = types;
        this.priorities = priorities;
        this.comments = comments;
        this.timeEntries = timeEntries;
        this.zone = ZoneId.of(timeZone);
    }

    /** Returns project and manager-only assignee filter options within visible tasks.
     * @param actorId current account
     * @return filter options */
    public TaskViews.FilterOptions filterOptions(Long actorId) {
        User actor = active(users.findWithRolesById(actorId).orElse(null));
        boolean manager = hasRole(actor, RoleCode.MANAGER);
        boolean executor = hasRole(actor, RoleCode.EXECUTOR);
        if (!manager && !executor) throw denied();
        var visibleProjects = tasks.findVisibleProjects(actorId, manager, executor).stream()
                .map(option -> new TaskViews.Option(option.getId(), option.getName())).toList();
        var assignees = manager ? tasks.findManagedAssignees(actorId).stream()
                .map(option -> new TaskViews.Option(option.getId(), option.getName())).toList()
                : List.<TaskViews.Option>of();
        return new TaskViews.FilterOptions(visibleProjects, assignees);
    }

    /** Searches visible tasks.
     * @param actorId current account
     * @param filters optional search limits
     * @param page zero-based page
     * @param size page capacity
     * @return visible tasks */
    public PageResponse<TaskViews.Summary> list(Long actorId, TaskRequests.Filters filters, int page, int size) {
        User actor = active(users.findWithRolesById(actorId).orElse(null));
        boolean manager = hasRole(actor, RoleCode.MANAGER);
        boolean executor = hasRole(actor, RoleCode.EXECUTOR);
        if (!manager && !executor || filters.assigneeId() != null && !manager) {
            throw denied();
        }
        var pagination = pageRequest(page, size, Sort.by("dueDate", "id"));
        if (!isCalendarYear(filters.dueFrom()) || !isCalendarYear(filters.dueTo())
                || filters.dueFrom() != null && filters.dueTo() != null && filters.dueFrom().isAfter(filters.dueTo())) {
            throw invalid("Проверьте диапазон сроков.");
        }
        Specification<Task> scope = (task, query, cb) -> {
            var owner = manager ? cb.equal(task.get("project").get("manager").get("id"), actorId) : cb.disjunction();
            var assigned = executor && filters.assigneeId() == null
                    ? cb.equal(task.get("assignee").get("id"), actorId) : cb.disjunction();
            var conditions = new ArrayList<Predicate>();
            conditions.add(cb.or(owner, assigned));
            if (filters.projectId() != null) conditions.add(cb.equal(task.get("project").get("id"), filters.projectId()));
            if (filters.status() != null) conditions.add(cb.equal(task.get("status"), filters.status()));
            if (filters.dueFrom() != null) conditions.add(cb.greaterThanOrEqualTo(task.get("dueDate"), filters.dueFrom()));
            if (filters.dueTo() != null) conditions.add(cb.lessThanOrEqualTo(task.get("dueDate"), filters.dueTo()));
            if (filters.assigneeId() != null) conditions.add(cb.equal(task.get("assignee").get("id"), filters.assigneeId()));
            return cb.and(conditions);
        };
        return PageResponse.from(tasks.findAll(scope, pagination).map(TaskViews.Summary::from));
    }

    /** Reads an owned or assigned task.
     * @param actorId current account
     * @param id task identity
     * @return task card */
    public TaskViews.Detail get(Long actorId, Long id) {
        return TaskViews.Detail.from(readable(actorId, id));
    }

    /** Creates an assigned task with server-selected author and initial status.
     * @param actorId current manager
     * @param projectId owned project
     * @param command task fields
     * @return saved task card */
    @Transactional
    public TaskViews.Detail create(Long actorId, Long projectId, TaskRequests.Create command) {
        var locked = lockUsers(actorId, command.assigneeId());
        requireManager(locked.get(actorId));
        var references = lockReferences(command);
        Project project = lockOwnedProject(projectId, actorId);
        User assignee = locked.get(command.assigneeId());
        requireAssignable(projectId, assignee);
        checkReferences(references, null);
        Task task = new Task(project, locked.get(actorId), assignee, references.type(), references.priority(),
                command.title(), command.description(), deadline(command.dueDate()), TaskStatus.NEW);
        return TaskViews.Detail.from(tasks.saveAndFlush(task));
    }

    /** Updates or reassigns a task under the expected version.
     * @param actorId current manager
     * @param id task identity
     * @param command edited fields
     * @return saved task card */
    @Transactional
    public TaskViews.Detail update(Long actorId, Long id, TaskRequests.Update command) {
        var location = tasks.findOwnedLocation(id, actorId).orElseThrow(TaskService::notFound);
        // The preliminary read only returns identities. Recheck the assignment under the task lock.
        var locked = lockUsers(actorId, location.getAssigneeId(), command.assigneeId());
        requireManager(locked.get(actorId));
        var references = lockReferences(command);
        lockOwnedProject(location.getProjectId(), actorId);
        Task task = tasks.findLockedById(id).orElseThrow(TaskService::notFound);
        checkVersion(task, command.expectedVersion(), location);
        if (!task.getStatus().isOpen()) {
            throw conflict("Редактировать можно только незавершённую задачу.");
        }
        User assignee = locked.get(command.assigneeId());
        if (!task.getAssignee().getId().equals(command.assigneeId())) {
            requireAssignable(location.getProjectId(), assignee);
        }
        checkReferences(references, task);
        task.updateDetails(command.title(), command.description(), assignee, deadline(command.dueDate()),
                references.type(), references.priority());
        tasks.flush();
        return TaskViews.Detail.from(task);
    }

    /** Lists the comments on a readable task, including closed tasks.
     * @param actorId current account
     * @param id task identity
     * @param page zero-based page
     * @param size page capacity
     * @return comment page */
    public PageResponse<TaskHistoryViews.CommentView> comments(Long actorId, Long id, int page, int size) {
        readable(actorId, id);
        return PageResponse.from(comments.findByTask_Id(id, pageRequest(page, size, HISTORY_ORDER))
                .map(TaskHistoryViews.CommentView::from));
    }

    /** Reads one comment within the task.
     * @param actorId current account
     * @param id task identity
     * @param commentId comment identity
     * @return comment */
    public TaskHistoryViews.CommentView comment(Long actorId, Long id, Long commentId) {
        readable(actorId, id);
        return comments.findByIdAndTask_Id(commentId, id).map(TaskHistoryViews.CommentView::from)
                .orElseThrow(() -> new OperationException(404, "NOT_FOUND", "Комментарий не найден."));
    }

    /** Lists the work log and its total on a readable task.
     * @param actorId current account
     * @param id task identity
     * @param page zero-based page
     * @param size page capacity
     * @return page with task duration */
    public TaskHistoryViews.TimePage timeEntries(Long actorId, Long id, int page, int size) {
        readable(actorId, id);
        var entries = timeEntries.findByTask_Id(id, pageRequest(page, size, HISTORY_ORDER)).map(TaskHistoryViews.TimeView::from);
        return TaskHistoryViews.TimePage.from(entries, timeEntries.sumMinutes(id), LocalDate.now(zone));
    }

    /** Reads one entry within the task.
     * @param actorId current account
     * @param id task identity
     * @param entryId work entry identity
     * @return work entry */
    public TaskHistoryViews.TimeView timeEntry(Long actorId, Long id, Long entryId) {
        readable(actorId, id);
        return timeEntries.findByIdAndTask_Id(entryId, id).map(TaskHistoryViews.TimeView::from)
                .orElseThrow(() -> new OperationException(404, "NOT_FOUND", "Запись времени не найдена."));
    }

    /** Changes the working state, optionally correcting the assignment when reopened.
     * @param actorId current account
     * @param id task identity
     * @param command requested transition
     * @return updated task card */
    @Transactional
    public TaskViews.Detail changeStatus(Long actorId, Long id, TaskRequests.ChangeStatus command) {
        var context = lockActivity(actorId, id, command.expectedVersion(), command.assigneeId());
        Task task = context.task();
        TaskStatus current = task.getStatus();
        TaskStatus target = command.targetStatus();
        boolean reopening = !current.isOpen() && target == TaskStatus.NEW;
        if (command.assigneeId() != null && !reopening) {
            throw invalid("Сменить исполнителя этой командой можно только при повторном открытии.");
        }
        if (current == target) return TaskViews.Detail.from(task);

        boolean starting = current == TaskStatus.NEW && target == TaskStatus.IN_PROGRESS;
        boolean finishing = current == TaskStatus.IN_PROGRESS && target == TaskStatus.DONE;
        boolean cancelling = current.isOpen() && target == TaskStatus.CANCELLED;
        if (!starting && !finishing && !cancelling && !reopening) {
            throw conflict("Такой переход статуса недоступен.");
        }
        if ((cancelling || reopening) && !context.access().manager()) throw denied();

        Long assigneeId = reopening && command.assigneeId() != null ? command.assigneeId() : task.getAssignee().getId();
        User assignee = context.users().get(assigneeId);
        if (starting || reopening) requireAssignable(task.getProject().getId(), assignee);
        task.transitionTo(target, assignee);
        tasks.flush();
        return TaskViews.Detail.from(task);
    }

    /** Appends a comment and advances the parent version atomically.
     * @param actorId current account
     * @param id task identity
     * @param command comment text and expected task version
     * @return saved comment and new version */
    @Transactional
    public TaskHistoryViews.CommentResult addComment(Long actorId, Long id, TaskRequests.AddComment command) {
        var context = lockActivity(actorId, id, command.expectedTaskVersion(), null);
        requireOpen(context.task());
        var comment = comments.saveAndFlush(new Comment(context.task(), context.users().get(actorId), command.body()));
        var view = TaskHistoryViews.CommentView.from(comment);
        return new TaskHistoryViews.CommentResult(view, advance(context.task()));
    }

    /** Appends work by the current executor and advances the parent version.
     * @param actorId current account
     * @param id task identity
     * @param command duration, calendar date and expected task version
     * @return saved entry and new version */
    @Transactional
    public TaskHistoryViews.TimeResult addTime(Long actorId, Long id, TaskRequests.AddTime command) {
        var context = lockActivity(actorId, id, command.expectedTaskVersion(), null);
        if (!context.access().executor()) throw denied();
        requireOpen(context.task());
        LocalDate workDate = calendarDate(command.workDate(), "Укажите действительную дату работы.");
        if (workDate.isAfter(LocalDate.now(zone))) throw invalid("Дата работы не может быть позже сегодняшней.");
        var entry = timeEntries.saveAndFlush(new TimeEntry(context.task(), context.users().get(actorId),
                workDate, command.minutes()));
        var view = TaskHistoryViews.TimeView.from(entry);
        return new TaskHistoryViews.TimeResult(view, advance(context.task()));
    }

    private Task readable(Long actorId, Long id) {
        User actor = active(users.findWithRolesById(actorId).orElse(null));
        Task task = tasks.findById(id).orElseThrow(TaskService::notFound);
        access(task, actor);
        return task;
    }

    private Activity lockActivity(Long actorId, Long id, long expectedVersion, Long replacementId) {
        var location = tasks.findActivityLocation(id, actorId).orElseThrow(TaskService::notFound);
        var locked = lockUsers(actorId, location.getAssigneeId(), replacementId);
        User actor = active(locked.get(actorId));
        projects.findLockedById(location.getProjectId()).orElseThrow(TaskService::notFound);
        Task task = tasks.findLockedById(id).orElseThrow(TaskService::notFound);
        Access access = access(task, actor);
        checkVersion(task, expectedVersion, location);
        return new Activity(task, access, locked);
    }

    private static Access access(Task task, User actor) {
        boolean manager = hasRole(actor, RoleCode.MANAGER) && task.getProject().getManager().getId().equals(actor.getId());
        boolean executor = hasRole(actor, RoleCode.EXECUTOR) && task.getAssignee().getId().equals(actor.getId());
        if (!manager && !executor) throw notFound();
        return new Access(manager, executor);
    }

    private static void checkVersion(Task task, long expectedVersion, TaskRepository.Location location) {
        if (task.getVersion() != expectedVersion || !task.getAssignee().getId().equals(location.getAssigneeId())) {
            throw stale();
        }
    }

    private long advance(Task task) {
        long version = task.getVersion();
        if (tasks.advanceVersion(task.getId(), version) != 1) throw stale();
        return version + 1;
    }

    private static void requireOpen(Task task) {
        if (!task.getStatus().isOpen()) throw conflict("Добавлять записи можно только в незавершённую задачу.");
    }

    private Map<Long, User> lockUsers(Long actorId, Long... targets) {
        var ids = new TreeSet<Long>();
        ids.add(actorId);
        for (Long target : targets) {
            if (target != null) ids.add(target);
        }
        var locked = new HashMap<Long, User>();
        for (Long id : ids) locked.put(id, users.findLockedById(id).orElse(null));
        return locked;
    }

    private References lockReferences(TaskRequests.Details command) {
        return new References(types.findLockedById(command.typeId()).orElse(null),
                priorities.findLockedById(command.priorityId()).orElse(null));
    }

    private Project lockOwnedProject(Long id, Long actorId) {
        Project project = projects.findLockedById(id).orElse(null);
        if (project == null || !project.getManager().getId().equals(actorId)) {
            throw new OperationException(404, "NOT_FOUND", "Проект не найден.");
        }
        return project;
    }

    private void requireAssignable(Long projectId, User user) {
        if (user == null || !user.isActive() || !hasRole(user, RoleCode.EXECUTOR)
                || !memberships.existsById(new ProjectMembershipId(projectId, user.getId()))) {
            throw conflict("Выберите активного исполнителя из участников проекта.");
        }
    }

    private static void checkReferences(References selected, Task prior) {
        if (selected.type() == null || !selected.type().isActive()
                && (prior == null || !prior.getType().getId().equals(selected.type().getId()))) {
            throw conflict("Выберите действующий тип задачи.");
        }
        if (selected.priority() == null || !selected.priority().isActive()
                && (prior == null || !prior.getPriority().getId().equals(selected.priority().getId()))) {
            throw conflict("Выберите действующий приоритет.");
        }
    }

    private static LocalDate deadline(String value) {
        return calendarDate(value, "Укажите действительную календарную дату срока.");
    }

    private static LocalDate calendarDate(String value, String message) {
        try {
            LocalDate result = LocalDate.parse(value);
            if (isCalendarYear(result)) return result;
        } catch (DateTimeParseException ignored) {
            // Invalid calendar dates use the same input error.
        }
        throw invalid(message);
    }

    private static boolean isCalendarYear(LocalDate date) {
        return date == null || date.getYear() >= 1 && date.getYear() <= 9999;
    }

    private static User active(User user) {
        if (user == null || !user.isActive()) {
            throw new OperationException(401, "AUTHENTICATION_REQUIRED", "Войдите в систему.");
        }
        return user;
    }

    private static void requireManager(User user) {
        if (!hasRole(active(user), RoleCode.MANAGER)) throw denied();
    }

    private static boolean hasRole(User user, RoleCode code) {
        return user.getRoles().stream().anyMatch(role -> role.getCode() == code);
    }

    private static OperationException notFound() {
        return new OperationException(404, "NOT_FOUND", "Задача не найдена.");
    }

    private static OperationException denied() {
        return new OperationException(403, "ACCESS_DENIED", "Действие недоступно.");
    }

    private static OperationException invalid(String message) {
        return new OperationException(400, "VALIDATION_ERROR", message);
    }

    private static OperationException conflict(String message) {
        return new OperationException(409, "RULE_CONFLICT", message);
    }

    private static OperationException stale() {
        return new OperationException(409, "STALE_VERSION", "Данные изменились. Обновите карточку и повторите действие.");
    }

    private static PageRequest pageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw invalid("Проверьте номер страницы и размер списка (1–100).");
        }
        return PageRequest.of(page, size, sort);
    }

    private record References(TaskType type, TaskPriority priority) { }
    private record Access(boolean manager, boolean executor) { }
    private record Activity(Task task, Access access, Map<Long, User> users) { }
}
