package ru.vehxi.taskflow.projects.domain;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import ru.vehxi.taskflow.users.domain.User;
/** Current participation, independent of historical task assignments. */
@Entity @Table(name = "project_members")
public class ProjectMembership {
    @EmbeddedId private ProjectMembershipId id;
    @MapsId("projectId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id") private Project project;
    @MapsId("userId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id") private User user;
    @Generated(event = EventType.INSERT)
    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private Instant joinedAt;
    /** Initializes a membership for persistence. */
    protected ProjectMembership() { }
    /**
     * Creates participation for persisted objects.
     *
     * @param project the project
     *
     * @param user the participant
     */
    public ProjectMembership(Project project, User user) {
        this.project = project; this.user = user;
        this.id = new ProjectMembershipId(project.getId(), user.getId());
    }
    /**
     * Returns the composite identity.
     *
     * @return the composite identity
     */
    public ProjectMembershipId getId() { return id; }
    /**
     * Returns the project.
     *
     * @return the project
     */
    public Project getProject() { return project; }
    /**
     * Returns the participant.
     *
     * @return the participant
     */
    public User getUser() { return user; }
    /**
     * Returns the creation time.
     *
     * @return the creation time
     */
    public Instant getJoinedAt() { return joinedAt; }
}
