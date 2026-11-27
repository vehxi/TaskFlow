package ru.vehxi.taskflow.references.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import ru.vehxi.taskflow.references.domain.*;
/** Persistence access for tasktype objects; HTTP access is checked by services. */
public interface TaskTypeRepository extends JpaRepository<TaskType, Long> {
    /** Locks the type during selection.
     * @param id type key
     * @return selected type */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select t from TaskType t where t.id = :id")
    Optional<TaskType> findLockedById(@Param("id") Long id);
}
