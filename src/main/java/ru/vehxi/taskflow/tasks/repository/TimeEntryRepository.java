package ru.vehxi.taskflow.tasks.repository;
import org.springframework.data.jpa.repository.*;
import ru.vehxi.taskflow.tasks.domain.*;
/** Persistence access for timeentry objects; HTTP access is checked by services. */
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
}
