package ru.vehxi.taskflow.projects.repository;
import org.springframework.data.jpa.repository.*;
import ru.vehxi.taskflow.projects.domain.*;
/** Persistence access for projectmembership objects; HTTP access is checked by services. */
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, ProjectMembershipId> {
}
