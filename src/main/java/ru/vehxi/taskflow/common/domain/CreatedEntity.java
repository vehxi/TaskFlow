package ru.vehxi.taskflow.common.domain;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
/** Persistent object with a creation time supplied by the database. */
@MappedSuperclass
public abstract class CreatedEntity extends IdentityEntity {
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
    /** Initializes an entity for persistence. */
    protected CreatedEntity() { }
    /**
     * Returns the immutable creation time.
     *
     * @return the immutable creation time
     */
    public Instant getCreatedAt() { return createdAt; }
}
