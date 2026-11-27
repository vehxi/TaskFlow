package ru.vehxi.taskflow.tasks.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import ru.vehxi.taskflow.tasks.domain.*;
/** Persistence access for task objects; HTTP access is checked by services. */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    /** {@inheritDoc} */
    @Override
    @EntityGraph(attributePaths = {"project", "assignee"})
    Page<Task> findAll(Specification<Task> scope, Pageable pageable);

    /** Lightweight read used to determine the order of command locks.
     * @param id task key
     * @param managerId owning manager
     * @return task location if owned */
    @Query("""
            select t.project.id as projectId, t.assignee.id as assigneeId from Task t
            where t.id = :id and t.project.manager.id = :managerId
            """)
    Optional<Location> findOwnedLocation(@Param("id") Long id, @Param("managerId") Long managerId);

    /** Lists projects represented in the visible task list.
     * @param actorId current account
     * @param manager whether ownership grants access
     * @param executor whether assignment grants access
     * @return projects of visible tasks */
    @Query("""
            select distinct p.id as id, p.name as name from Task t join t.project p
            where (:manager = true and p.manager.id = :actorId) or (:executor = true and t.assignee.id = :actorId)
            order by p.name, p.id
            """)
    java.util.List<Option> findVisibleProjects(@Param("actorId") Long actorId, @Param("manager") boolean manager,
            @Param("executor") boolean executor);

    /** Lists assignees in the manager's own projects.
     * @param managerId current manager
     * @return current assignees within the manager's projects */
    @Query("""
            select distinct u.id as id, u.name as name from Task t join t.assignee u
            where t.project.manager.id = :managerId order by u.name, u.id
            """)
    java.util.List<Option> findManagedAssignees(@Param("managerId") Long managerId);

    /** Minimal choice for a list filter. */
    interface Option {
        /** {@return choice identity } */
        Long getId();
        /** {@return choice name } */
        String getName();
    }

    /** Returns a task locked for a command.
     * @param id task key
     * @return locked task */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findLockedById(@Param("id") Long id);

    /** Project and assignee identities used before locking. */
    interface Location {
        /** {@return project identity } */
        Long getProjectId();
        /** {@return assignee identity } */
        Long getAssigneeId();
    }
    /** Returns whether matching tasks exist.
     * @param id assignee identity
     * @param statuses relevant states
     * @return whether matching tasks exist */
    boolean existsByAssignee_IdAndStatusIn(Long id, java.util.Collection<TaskStatus> statuses);

    /** Returns whether the participant has a task in one of the given states in this project.
     * @param projectId project key
     * @param assigneeId participant key
     * @param statuses states to check
     * @return whether a matching task exists */
    boolean existsByProject_IdAndAssignee_IdAndStatusIn(Long projectId, Long assigneeId,
            java.util.Collection<TaskStatus> statuses);
}
