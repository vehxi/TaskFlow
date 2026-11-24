package ru.vehxi.taskflow.projects.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import ru.vehxi.taskflow.projects.domain.*;
import ru.vehxi.taskflow.users.domain.User;
/** Persistence access for projectmembership objects; HTTP access is checked by services. */
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, ProjectMembershipId> {
    /** Returns members, including any subsequently deactivated accounts.
     * @param projectId project key
     * @param pageable stable pagination
     * @return participant accounts */
    @Query("select u from ProjectMembership m join m.user u where m.id.projectId = :projectId")
    Page<User> findUsers(@Param("projectId") Long projectId, Pageable pageable);
}
