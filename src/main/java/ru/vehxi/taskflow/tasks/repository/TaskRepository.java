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
}
