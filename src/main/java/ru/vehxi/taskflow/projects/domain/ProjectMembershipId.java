package ru.vehxi.taskflow.projects.domain;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
/** Composite identity of a project participant. */
@Embeddable
public class ProjectMembershipId implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Project part of the composite identity. */
    @Column(name = "project_id") private Long projectId;
    /** User part of the composite identity. */
    @Column(name = "user_id") private Long userId;
    /** Initializes the key for persistence. */
    protected ProjectMembershipId() { }
    /**
     * Initializes the component with the supplied parameters.
     *
     * @param projectId project key
     * @param userId user key
     */
    public ProjectMembershipId(Long projectId, Long userId) { this.projectId = projectId; this.userId = userId; }
    /**
     * Returns the project key.
     *
     * @return the project key
     */
    public Long getProjectId() { return projectId; }
    /**
     * Returns the participant key.
     *
     * @return the participant key
     */
    public Long getUserId() { return userId; }
    /** {@inheritDoc} */
    @Override public boolean equals(Object other) {
        return this == other || other instanceof ProjectMembershipId key
                && Objects.equals(projectId, key.projectId) && Objects.equals(userId, key.userId);
    }
    /** {@inheritDoc} */
    @Override public int hashCode() { return Objects.hash(projectId, userId); }
}
