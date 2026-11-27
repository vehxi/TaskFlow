package ru.vehxi.taskflow.references.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import ru.vehxi.taskflow.references.domain.*;
/** Persistence access for taskpriority objects; HTTP access is checked by services. */
public interface TaskPriorityRepository extends JpaRepository<TaskPriority, Long> {
    /** Locks the priority during selection.
     * @param id priority key
     * @return selected priority */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select p from TaskPriority p where p.id = :id")
    Optional<TaskPriority> findLockedById(@Param("id") Long id);
}
