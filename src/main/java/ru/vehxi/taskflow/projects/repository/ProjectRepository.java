package ru.vehxi.taskflow.projects.repository;
import org.springframework.data.jpa.repository.*;
import ru.vehxi.taskflow.projects.domain.*;
/** Persistence access for project objects; HTTP access is checked by services. */
public interface ProjectRepository extends JpaRepository<Project, Long> {
    /** Returns whether any project belongs to this manager.
     * @param id manager identity
     * @return whether any project belongs to this manager */
    boolean existsByManager_Id(Long id);
}
