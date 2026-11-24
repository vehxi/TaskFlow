package ru.vehxi.taskflow.projects.service;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.vehxi.taskflow.common.web.OperationException;
import ru.vehxi.taskflow.common.web.PageResponse;
import ru.vehxi.taskflow.projects.domain.Project;
import ru.vehxi.taskflow.projects.domain.ProjectMembership;
import ru.vehxi.taskflow.projects.domain.ProjectMembershipId;
import ru.vehxi.taskflow.projects.repository.ProjectMembershipRepository;
import ru.vehxi.taskflow.projects.repository.ProjectRepository;
import ru.vehxi.taskflow.projects.web.*;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;
import ru.vehxi.taskflow.tasks.repository.TaskRepository;
import ru.vehxi.taskflow.users.domain.RoleCode;
import ru.vehxi.taskflow.users.domain.User;
import ru.vehxi.taskflow.users.repository.UserRepository;

/** Project access, transactions and membership rules. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProjectService {
    private static final Set<TaskStatus> OPEN_TASKS = Set.of(TaskStatus.NEW, TaskStatus.IN_PROGRESS);
    private final ProjectRepository projects;
    private final ProjectMembershipRepository members;
    private final UserRepository users;
    private final TaskRepository tasks;

    /** Creates project operations.
     * @param projects project storage
     * @param members membership storage
     * @param users account storage
     * @param tasks task storage */
    public ProjectService(ProjectRepository projects, ProjectMembershipRepository members, UserRepository users,
            TaskRepository tasks) {
        this.projects = projects;
        this.members = members;
        this.users = users;
        this.tasks = tasks;
    }

    /** Lists projects visible to the current user.
     * @param actorId current account
     * @param page zero-based page
     * @param size page capacity
     * @return visible projects */
    public PageResponse<ProjectSummaryDto> list(Long actorId, int page, int size) {
        User actor = reader(actorId);
        return PageResponse.from(projects.findVisible(actorId, hasRole(actor, RoleCode.MANAGER),
                hasRole(actor, RoleCode.EXECUTOR), pageRequest(page, size)).map(ProjectSummaryDto::from));
    }

    /** Looks up a project through ownership or executor membership.
     * @param actorId current account
     * @param id project identity
     * @return visible project */
    public ProjectSummaryDto get(Long actorId, Long id) {
        User actor = reader(actorId);
        Project project = projects.findById(id).orElseThrow(ProjectService::projectNotFound);
        if (!(hasRole(actor, RoleCode.MANAGER) && project.getManager().getId().equals(actorId))
                && !(hasRole(actor, RoleCode.EXECUTOR) && members.existsById(new ProjectMembershipId(id, actorId)))) {
            throw projectNotFound();
        }
        return ProjectSummaryDto.from(project);
    }

    /** Lists the current project participants and version from the same database snapshot.
     * @param actorId current account
     * @param id project identity
     * @param page zero-based page
     * @param size page capacity
     * @return participant page */
    public ProjectMembersPage members(Long actorId, Long id, int page, int size) {
        requireManager(users.findWithRolesById(actorId).orElse(null));
        Project project = owned(projects.findById(id).orElse(null), actorId);
        return ProjectMembersPage.from(members.findUsers(id, pageRequest(page, size)).map(ProjectMemberDto::from),
                project.getVersion());
    }

    /** Lists active executors for a manager.
     * @param actorId current account
     * @param projectId optional owned project whose existing members shall be excluded
     * @param page zero-based page
     * @param size page capacity
     * @return eligible candidates */
    public PageResponse<ProjectMemberDto> candidates(Long actorId, Long projectId, int page, int size) {
        requireManager(users.findWithRolesById(actorId).orElse(null));
        if (projectId != null) {
            owned(projects.findById(projectId).orElse(null), actorId);
        }
        return PageResponse.from(users.findAssignable(RoleCode.EXECUTOR, projectId == null ? 0 : projectId,
                pageRequest(page, size)).map(ProjectMemberDto::from));
    }

    /** Creates a project owned by the current manager.
     * @param actorId current account
     * @param command new project details
     * @return saved project */
    @Transactional
    public ProjectSummaryDto create(Long actorId, ProjectRequests.Create command) {
        User manager = requireManager(users.findLockedById(actorId).orElse(null));
        return ProjectSummaryDto.from(projects.saveAndFlush(new Project(manager, command.name(), command.description())));
    }

    /** Updates the details of an owned project.
     * @param actorId current account
     * @param id project identity
     * @param command new details and expected version
     * @return saved project */
    @Transactional
    public ProjectSummaryDto update(Long actorId, Long id, ProjectRequests.Update command) {
        requireManager(users.findLockedById(actorId).orElse(null));
        Project project = lockOwned(id, actorId, command.expectedVersion());
        project.updateDetails(command.name(), command.description());
        projects.flush();
        return ProjectSummaryDto.from(project);
    }

    /** Adds an active executor to an owned project.
     * @param actorId current account
     * @param id project identity
     * @param command participant and expected version
     * @return new project version */
    @Transactional
    public long addMember(Long actorId, Long id, ProjectRequests.AddMember command) {
        Long userId = command.userId();
        User target = lockUsers(actorId, userId);
        Project project = lockOwned(id, actorId, command.expectedProjectVersion());
        if (target == null || !target.isActive() || !hasRole(target, RoleCode.EXECUTOR)) {
            throw new OperationException(409, "RULE_CONFLICT", "Выберите активного исполнителя.");
        }
        if (members.existsById(new ProjectMembershipId(id, userId))) {
            throw new OperationException(409, "DUPLICATE_VALUE", "Исполнитель уже участвует в проекте.");
        }
        members.save(new ProjectMembership(project, target));
        return advance(project);
    }

    /** Removes a participant when they have no open tasks in this project.
     * @param actorId current account
     * @param id project identity
     * @param userId participant identity
     * @param expectedVersion version supplied by the editor
     * @return new project version */
    @Transactional
    public long removeMember(Long actorId, Long id, Long userId, long expectedVersion) {
        lockUsers(actorId, userId);
        Project project = lockOwned(id, actorId, expectedVersion);
        var membership = members.findById(new ProjectMembershipId(id, userId)).orElseThrow(
                () -> new OperationException(404, "NOT_FOUND", "Участник проекта не найден."));
        if (tasks.existsByProject_IdAndAssignee_IdAndStatusIn(id, userId, OPEN_TASKS)) {
            throw new OperationException(409, "RULE_CONFLICT", "Сначала завершите или переназначьте задачи участника.");
        }
        members.delete(membership);
        return advance(project);
    }

    private User lockUsers(Long actorId, Long targetId) {
        // Account locks precede the project lock and follow ascending IDs.
        if (actorId.equals(targetId)) {
            return requireManager(users.findLockedById(actorId).orElse(null));
        }
        if (actorId < targetId) {
            requireManager(users.findLockedById(actorId).orElse(null));
            return users.findLockedById(targetId).orElse(null);
        }
        User target = users.findLockedById(targetId).orElse(null);
        requireManager(users.findLockedById(actorId).orElse(null));
        return target;
    }

    private Project lockOwned(Long id, Long actorId, long expectedVersion) {
        Project project = owned(projects.findLockedById(id).orElse(null), actorId);
        if (project.getVersion() != expectedVersion) {
            throw stale();
        }
        return project;
    }

    private long advance(Project project) {
        long previous = project.getVersion();
        if (projects.advanceVersion(project.getId(), previous) != 1) {
            throw stale();
        }
        return previous + 1;
    }

    private static Project owned(Project project, Long actorId) {
        if (project == null || !project.getManager().getId().equals(actorId)) {
            throw projectNotFound();
        }
        return project;
    }

    private User reader(Long actorId) {
        User actor = active(users.findWithRolesById(actorId).orElse(null));
        if (!hasRole(actor, RoleCode.MANAGER) && !hasRole(actor, RoleCode.EXECUTOR)) {
            throw denied();
        }
        return actor;
    }

    private static User requireManager(User actor) {
        if (!hasRole(active(actor), RoleCode.MANAGER)) {
            throw denied();
        }
        return actor;
    }

    private static User active(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new OperationException(401, "AUTHENTICATION_REQUIRED", "Войдите в систему.");
        }
        return actor;
    }

    private static boolean hasRole(User user, RoleCode code) {
        return user.getRoles().stream().anyMatch(role -> role.getCode() == code);
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new OperationException(400, "VALIDATION_ERROR", "Проверьте номер страницы и размер списка (1–100).");
        }
        return PageRequest.of(page, size, Sort.by("id"));
    }

    private static OperationException projectNotFound() {
        return new OperationException(404, "NOT_FOUND", "Проект не найден.");
    }

    private static OperationException denied() {
        return new OperationException(403, "ACCESS_DENIED", "Действие недоступно.");
    }

    private static OperationException stale() {
        return new OperationException(409, "STALE_VERSION", "Данные изменились. Обновите карточку и повторите действие.");
    }
}
