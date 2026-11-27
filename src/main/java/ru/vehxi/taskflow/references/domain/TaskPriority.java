package ru.vehxi.taskflow.references.domain;
import jakarta.persistence.*;
import ru.vehxi.taskflow.common.domain.VersionedEntity;
/** Persistent taskpriority data. */
@Entity @Table(name = "task_priorities")
public class TaskPriority extends VersionedEntity {
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false)
    private boolean active;
    /** Initializes an entity for persistence. */
    protected TaskPriority() { }
    /**
     * Creates an entity with the supplied data.
     *
     * @param name the name value
     *
     * @param active the active value
     */
    public TaskPriority(String name, boolean active) {
        this.name = name;
        this.active = active;
    }
    /**
     * Updates the label and whether it may be newly selected.
     * @param name the new label
     * @param active whether the value is available for new selections
     */
    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }
    /**
     * Returns the name value.
     *
     * @return the name value
     */
    public String getName() { return name; }
    /**
     * Returns the active value.
     *
     * @return the active value
     */
    public boolean isActive() { return active; }
}
