package ru.vehxi.taskflow.projects.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import ru.vehxi.taskflow.projects.domain.*;
/** Persistence access for project objects; HTTP access is checked by services. */
public interface ProjectRepository extends JpaRepository<Project, Long> {
    /** Returns whether any project belongs to this manager.
     * @param id manager identity
     * @return whether any project belongs to this manager */
    boolean existsByManager_Id(Long id);

    /** Selects projects in the union of the actor's applicable roles.
     * @param actorId account key
     * @param manager whether ownership grants access
     * @param executor whether membership grants access
     * @param pageable stable pagination
     * @return visible projects */
    @Query("""
            select p from Project p
            where (:manager = true and p.manager.id = :actorId)
               or (:executor = true and exists (
                   select m.id.userId from ProjectMembership m
                   where m.id.projectId = p.id and m.id.userId = :actorId))
            """)
    Page<Project> findVisible(@Param("actorId") Long actorId, @Param("manager") boolean manager,
            @Param("executor") boolean executor, Pageable pageable);

    /** Returns a project locked for a command.
     * @param id project key
     * @return locked project */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findLockedById(@Param("id") Long id);

    /** Advances the version after a change to the membership table.
     * @param id project key
     * @param version version held by the command
     * @return affected rows */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Project p set p.version = p.version + 1 where p.id = :id and p.version = :version")
    int advanceVersion(@Param("id") Long id, @Param("version") long version);
}
