package ru.vehxi.taskflow.references.repository;
import org.springframework.data.jpa.repository.*;
import ru.vehxi.taskflow.references.domain.*;
/** Persistence access for taskpriority objects; HTTP access is checked by services. */
public interface TaskPriorityRepository extends JpaRepository<TaskPriority, Long> {
}
