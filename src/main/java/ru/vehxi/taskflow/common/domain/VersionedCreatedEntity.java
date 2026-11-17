package ru.vehxi.taskflow.common.domain;
import jakarta.persistence.*;
/** Persistent object protected against lost updates. */
@MappedSuperclass
public abstract class VersionedCreatedEntity extends CreatedEntity {
    @Version @Column(nullable = false)
    private long version;
    /** Initializes an entity for persistence. */
    protected VersionedCreatedEntity() { }
    /**
     * Returns the current optimistic locking version.
     *
     * @return the current optimistic locking version
     */
    public long getVersion() { return version; }
}
