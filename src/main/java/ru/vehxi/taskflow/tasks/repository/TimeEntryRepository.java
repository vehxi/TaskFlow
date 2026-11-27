package ru.vehxi.taskflow.tasks.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import ru.vehxi.taskflow.tasks.domain.TimeEntry;
/** Persistence access for timeentry objects; HTTP access is checked by services. */
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    /** Lists time entries and authors for the task.
     * @param taskId parent task key
     * @param pageable page and ordering
     * @return time entry page */
    @EntityGraph(attributePaths = "author")
    Page<TimeEntry> findByTask_Id(Long taskId, Pageable pageable);

    /** Reads a time entry within the authorized task.
     * @param id time entry key
     * @param taskId parent task key
     * @return matching entry */
    Optional<TimeEntry> findByIdAndTask_Id(Long id, Long taskId);

    /** Totals all time entries, irrespective of the current assignee.
     * @param taskId parent task key
     * @return minutes across all pages */
    @Query("select coalesce(sum(e.minutes), 0) from TimeEntry e where e.task.id = :taskId")
    long sumMinutes(@Param("taskId") Long taskId);
}
