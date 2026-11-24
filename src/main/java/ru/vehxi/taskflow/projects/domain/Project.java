package ru.vehxi.taskflow.projects.domain;
import jakarta.persistence.*;
import ru.vehxi.taskflow.common.domain.VersionedCreatedEntity;
import ru.vehxi.taskflow.users.domain.User;
/** Persistent project data. */
@Entity @Table(name = "projects")
public class Project extends VersionedCreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "manager_id", nullable = false)
    private User manager;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    /** Initializes an entity for persistence. */
    protected Project() { }
    /**
     * Creates an entity with the supplied data.
     *
     * @param manager the manager value
     *
     * @param name the name value
     *
     * @param description the description value
     */
    public Project(User manager, String name, String description) {
        this.manager = manager;
        this.name = name;
        this.description = description;
    }
    /**
     * Returns the manager value.
     *
     * @return the manager value
     */
    public User getManager() { return manager; }
    /**
     * Returns the name value.
     *
     * @return the name value
     */
    public String getName() { return name; }
    /**
     * Returns the description value.
     *
     * @return the description value
     */
    public String getDescription() { return description; }

    /** Updates the editable details.
     * @param name normalized name
     * @param description normalized description */
    public void updateDetails(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
