package ru.vehxi.taskflow.references.repository;
import org.springframework.data.jpa.repository.*;
import ru.vehxi.taskflow.references.domain.*;
/** Persistence access for tasktype objects; HTTP access is checked by services. */
public interface TaskTypeRepository extends JpaRepository<TaskType, Long> {
}
