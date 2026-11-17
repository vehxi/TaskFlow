package ru.vehxi.taskflow.users.service;

import jakarta.validation.Validator;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vehxi.taskflow.common.web.*;
import ru.vehxi.taskflow.projects.repository.ProjectRepository;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;
import ru.vehxi.taskflow.tasks.repository.TaskRepository;
import ru.vehxi.taskflow.users.domain.*;
import ru.vehxi.taskflow.users.repository.*;
import ru.vehxi.taskflow.users.web.*;

/** Transactional administration with fresh actor checks and ordered user locks. */
@Service
@Transactional(readOnly = true)
public class UserAdministrationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final Validator validator;

    /** Initializes the component with the supplied arguments.
     * @param users account storage
     * @param roles seeded roles
     * @param projects project storage
     *
     * @param tasks task storage
     * @param passwords secret encoder
     * @param jdbc shared transactional connection
     *
     * @param validator command validation */
    public UserAdministrationService(UserRepository users, RoleRepository roles, ProjectRepository projects,
            TaskRepository tasks, PasswordEncoder passwords, JdbcTemplate jdbc, Validator validator) {
        this.users = users; this.roles = roles; this.projects = projects; this.tasks = tasks;
        this.passwords = passwords; this.jdbc = jdbc; this.validator = validator;
    }

    /** Returns visible accounts.
     * @param actorId session identity
     * @param page zero-based page
     * @param size capacity
     * @return visible accounts */
    public PageResponse<UserDto> list(Long actorId, int page, int size) {
        requireAdmin(users.findWithRolesById(actorId).orElseThrow(UserAdministrationService::authenticationRequired));
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new OperationException(400, "VALIDATION_ERROR", "Проверьте номер страницы и размер списка (1–100).");
        }
        return PageResponse.from(users.findAll(PageRequest.of(page, size, Sort.by("id"))).map(UserDto::from));
    }

    /** Returns public account.
     * @param actorId session identity
     * @param id account identity
     * @return public account */
    public UserDto get(Long actorId, Long id) {
        requireAdmin(users.findWithRolesById(actorId).orElseThrow(UserAdministrationService::authenticationRequired));
        return UserDto.from(users.findWithRolesById(id).orElseThrow(UserAdministrationService::notFound));
    }

    /** Returns saved account.
     * @param actorId session identity
     * @param command initial account data
     * @return saved account */
    @Transactional
    public UserDto create(Long actorId, CreateUserRequest command) {
        requireAdmin(users.findLockedById(actorId).orElseThrow(UserAdministrationService::authenticationRequired));
        validate(command);
        if (users.existsByEmail(command.email())) { throw duplicate(); }
        var assignedRoles = new HashSet<>(roles.findAllById(command.roles()));
        if (assignedRoles.size() != command.roles().size()) {
            throw new OperationException(409, "RULE_CONFLICT", "Выбранная роль недоступна.");
        }
        return UserDto.from(users.saveAndFlush(new User(command.name(), command.email(),
                passwords.encode(command.initialPassword()), assignedRoles)));
    }

    /** Returns saved account.
     * @param actorId session identity
     * @param id target account
     * @param command edited fields
     * @return saved account */
    @Transactional
    public UserDto update(Long actorId, Long id, UpdateUserRequest command) {
        var locked = new HashMap<Long, User>();
        var identities = new TreeSet<Long>();
        identities.add(actorId); identities.add(id);
        for (Long key : identities) {
            locked.put(key, users.findLockedById(key).orElse(null));
        }
        if (locked.get(actorId) == null) { throw authenticationRequired(); }
        requireAdmin(locked.get(actorId));
        if (locked.get(id) == null) { throw notFound(); }
        validate(command);
        User target = locked.get(id);
        if (target.getVersion() != command.expectedVersion()) {
            throw new OperationException(409, "STALE_VERSION", "Данные изменились. Обновите карточку и повторите действие.");
        }
        Set<RoleCode> currentRoles = UserDto.from(target).roles();
        if (currentRoles.contains(RoleCode.EXECUTOR) && !command.roles().contains(RoleCode.EXECUTOR)
                && tasks.existsByAssignee_IdAndStatusIn(id, Set.of(TaskStatus.NEW, TaskStatus.IN_PROGRESS))) {
            throw new OperationException(409, "RULE_CONFLICT", "Сначала завершите или переназначьте незавершённые задачи пользователя.");
        }
        if (currentRoles.contains(RoleCode.MANAGER) && !command.roles().contains(RoleCode.MANAGER)
                && projects.existsByManager_Id(id)) {
            throw new OperationException(409, "RULE_CONFLICT", "Нельзя снять роль руководителя, пока за пользователем закреплены проекты.");
        }
        if (!target.getEmail().equals(command.email()) && users.existsByEmail(command.email())) { throw duplicate(); }
        if (target.getName().equals(command.name()) && target.getEmail().equals(command.email())
                && target.isActive() == command.active() && currentRoles.equals(command.roles())) {
            return UserDto.from(target);
        }
        // The bulk update increments version exactly once, including a roles-only mutation.
        // It clears the persistence context; no managed collection is subsequently modified.
        if (users.updateAccount(id, command.expectedVersion(), command.name(), command.email(), command.active()) != 1) {
            throw new OperationException(409, "STALE_VERSION", "Данные изменились. Обновите карточку и повторите действие.");
        }
        for (RoleCode code : currentRoles) {
            if (!command.roles().contains(code)) {
                jdbc.update("DELETE FROM user_roles WHERE user_id = ? AND role_code = ?", id, code.name());
            }
        }
        for (RoleCode code : command.roles()) {
            if (!currentRoles.contains(code)) {
                jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", id, code.name());
            }
        }
        return UserDto.from(users.findWithRolesById(id).orElseThrow(UserAdministrationService::notFound));
    }

    private void validate(Object command) {
        var violations = validator.validate(command);
        if (!violations.isEmpty()) { throw new jakarta.validation.ConstraintViolationException(violations); }
    }
    private static void requireAdmin(User actor) {
        if (!actor.isActive()) { throw authenticationRequired(); }
        if (actor.getRoles().stream().noneMatch(role -> role.getCode() == RoleCode.ADMIN)) {
            throw new OperationException(403, "ACCESS_DENIED", "Действие недоступно.");
        }
    }
    private static OperationException authenticationRequired() {
        return new OperationException(401, "AUTHENTICATION_REQUIRED", "Войдите в систему.");
    }
    private static OperationException notFound() {
        return new OperationException(404, "NOT_FOUND", "Пользователь не найден.");
    }
    private static OperationException duplicate() {
        return new OperationException(409, "DUPLICATE_VALUE", "Этот email уже используется.");
    }
}
