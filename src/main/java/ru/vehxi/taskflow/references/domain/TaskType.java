package ru.vehxi.taskflow.references.domain;
import jakarta.persistence.*;
import ru.vehxi.taskflow.common.domain.VersionedEntity;
/** Persistent tasktype data. */
@Entity @Table(name = "task_types")
public class TaskType extends VersionedEntity {
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false)
    private boolean active;
    /** Initializes an entity for persistence. */
    protected TaskType() { }
    /**
     * Creates an entity with the supplied data.
     *
     * @param name the name value
     *
     * @param active the active value
     */
    public TaskType(String name, boolean active) {
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
