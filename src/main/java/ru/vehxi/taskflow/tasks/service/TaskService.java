package ru.vehxi.taskflow.tasks.service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
import ru.vehxi.taskflow.tasks.domain.TaskStatus;
import ru.vehxi.taskflow.tasks.repository.TaskRepository;
import ru.vehxi.taskflow.tasks.web.TaskRequests;
import ru.vehxi.taskflow.tasks.web.TaskViews;
import ru.vehxi.taskflow.users.domain.RoleCode;
import ru.vehxi.taskflow.users.domain.User;
import ru.vehxi.taskflow.users.repository.UserRepository;

/** Authorizes, searches and saves tasks. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final UserRepository users;
    private final TaskTypeRepository types;
    private final TaskPriorityRepository priorities;

    /** Creates task operations.
     * @param tasks task storage
     * @param projects project storage
     * @param memberships project membership storage
     * @param users account storage
     * @param types type storage
     * @param priorities priority storage */
    public TaskService(TaskRepository tasks, ProjectRepository projects, ProjectMembershipRepository memberships,
            UserRepository users, TaskTypeRepository types, TaskPriorityRepository priorities) {
        this.tasks = tasks;
        this.projects = projects;
        this.memberships = memberships;
        this.users = users;
        this.types = types;
        this.priorities = priorities;
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
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw invalid("Проверьте номер страницы и размер списка (1–100).");
        }
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
        return PageResponse.from(tasks.findAll(scope, PageRequest.of(page, size, Sort.by("dueDate", "id")))
                .map(TaskViews.Summary::from));
    }

    /** Reads an owned or assigned task.
     * @param actorId current account
     * @param id task identity
     * @return task card */
    public TaskViews.Detail get(Long actorId, Long id) {
        User actor = active(users.findWithRolesById(actorId).orElse(null));
        Task task = tasks.findById(id).orElseThrow(TaskService::notFound);
        if (!(hasRole(actor, RoleCode.MANAGER) && task.getProject().getManager().getId().equals(actorId))
                && !(hasRole(actor, RoleCode.EXECUTOR) && task.getAssignee().getId().equals(actorId))) {
            throw notFound();
        }
        return TaskViews.Detail.from(task);
    }

    /** Creates an assigned task with server-selected author and initial status.
     * @param actorId current manager
     * @param projectId owned project
     * @param command task fields
     * @return saved task card */
    @Transactional
    public TaskViews.Detail create(Long actorId, Long projectId, TaskRequests.Create command) {
        var locked = lockUsers(actorId, command.assigneeId());
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
        var references = lockReferences(command);
        lockOwnedProject(location.getProjectId(), actorId);
        Task task = tasks.findLockedById(id).orElseThrow(TaskService::notFound);
        if (task.getVersion() != command.expectedVersion() || !task.getAssignee().getId().equals(location.getAssigneeId())) {
            throw new OperationException(409, "STALE_VERSION", "Данные изменились. Обновите карточку и повторите действие.");
        }
        if (task.getStatus() == TaskStatus.DONE || task.getStatus() == TaskStatus.CANCELLED) {
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

    private Map<Long, User> lockUsers(Long actorId, Long... targets) {
        var ids = new TreeSet<Long>();
        ids.add(actorId);
        for (Long target : targets) ids.add(target);
        var locked = new HashMap<Long, User>();
        for (Long id : ids) locked.put(id, users.findLockedById(id).orElse(null));
        requireManager(locked.get(actorId));
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
        try {
            LocalDate result = LocalDate.parse(value);
            if (isCalendarYear(result)) return result;
        } catch (DateTimeParseException ignored) {
            // Invalid calendar dates use the same input error.
        }
        throw invalid("Укажите действительную календарную дату срока.");
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

    private record References(TaskType type, TaskPriority priority) { }
}
