package ru.vehxi.taskflow.references.service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vehxi.taskflow.common.web.OperationException;
import ru.vehxi.taskflow.references.ReferenceValue;
import ru.vehxi.taskflow.references.domain.TaskPriority;
import ru.vehxi.taskflow.references.domain.TaskType;
import ru.vehxi.taskflow.references.repository.TaskPriorityRepository;
import ru.vehxi.taskflow.references.repository.TaskTypeRepository;
import ru.vehxi.taskflow.users.domain.RoleCode;
import ru.vehxi.taskflow.users.repository.UserRepository;

/** Maintains task types and priorities without removing historical references. */
@Service
public class ReferenceAdministrationService {
    private final TaskTypeRepository types;
    private final TaskPriorityRepository priorities;
    private final UserRepository users;

    /** @param types task type storage
     * @param priorities task priority storage
     * @param users user storage */
    public ReferenceAdministrationService(TaskTypeRepository types, TaskPriorityRepository priorities,
                                          UserRepository users) {
        this.types = types;
        this.priorities = priorities;
        this.users = users;
    }

    /** @param id type identity
     * @return the reference value */
    @Transactional(readOnly = true)
    public ReferenceValue getType(Long id) {
        return ReferenceValue.from(types.findById(id).orElseThrow(ReferenceAdministrationService::notFound));
    }

    /** @param id priority identity
     * @return the reference value */
    @Transactional(readOnly = true)
    public ReferenceValue getPriority(Long id) {
        return ReferenceValue.from(priorities.findById(id).orElseThrow(ReferenceAdministrationService::notFound));
    }

    /** @param actorId current account
     * @param name normalized label
     * @return the new active type */
    @Transactional
    public ReferenceValue createType(Long actorId, String name) {
        lockAdministrator(actorId);
        return ReferenceValue.from(types.saveAndFlush(new TaskType(name, true)));
    }

    /** @param actorId current account
     * @param name normalized label
     * @return the new active priority */
    @Transactional
    public ReferenceValue createPriority(Long actorId, String name) {
        lockAdministrator(actorId);
        return ReferenceValue.from(priorities.saveAndFlush(new TaskPriority(name, true)));
    }

    /** @param actorId current account
     * @param id type identity
     * @param name normalized label
     * @param active whether tasks can newly select the value
     * @param version expected version
     * @return the saved type */
    @Transactional
    public ReferenceValue updateType(Long actorId, Long id, String name, boolean active, long version) {
        lockAdministrator(actorId);
        var type = types.findLockedById(id).orElseThrow(ReferenceAdministrationService::notFound);
        checkVersion(type.getVersion(), version);
        type.update(name, active);
        types.flush();
        return ReferenceValue.from(type);
    }

    /** @param actorId current account
     * @param id priority identity
     * @param name normalized label
     * @param active whether tasks can newly select the value
     * @param version expected version
     * @return the saved priority */
    @Transactional
    public ReferenceValue updatePriority(Long actorId, Long id, String name, boolean active, long version) {
        lockAdministrator(actorId);
        var priority = priorities.findLockedById(id).orElseThrow(ReferenceAdministrationService::notFound);
        checkVersion(priority.getVersion(), version);
        priority.update(name, active);
        priorities.flush();
        return ReferenceValue.from(priority);
    }

    private void lockAdministrator(Long actorId) {
        var actor = users.findLockedById(actorId).filter(user -> user.isActive())
                .orElseThrow(() -> new OperationException(401, "AUTHENTICATION_REQUIRED", "Войдите в систему."));
        if (actor.getRoles().stream().noneMatch(role -> role.getCode() == RoleCode.ADMIN)) {
            throw new OperationException(403, "ACCESS_DENIED", "Действие недоступно.");
        }
    }

    private static void checkVersion(long actual, long expected) {
        if (actual != expected) {
            throw new OptimisticLockingFailureException("Reference value version has changed");
        }
    }

    private static OperationException notFound() {
        return new OperationException(404, "NOT_FOUND", "Значение справочника не найдено.");
    }
}
