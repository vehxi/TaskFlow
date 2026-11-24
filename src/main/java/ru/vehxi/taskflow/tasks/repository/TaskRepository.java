package ru.vehxi.taskflow.tasks.repository;
import org.springframework.data.jpa.repository.*;
import ru.vehxi.taskflow.tasks.domain.*;
/** Persistence access for task objects; HTTP access is checked by services. */
public interface TaskRepository extends JpaRepository<Task, Long> {
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
