package ru.vehxi.taskflow.common.domain;
import jakarta.persistence.*;
/** Persistent object protected against lost updates. */
@MappedSuperclass
public abstract class VersionedEntity extends IdentityEntity {
    @Version @Column(nullable = false)
    private long version;
    /** Initializes an entity for persistence. */
    protected VersionedEntity() { }
    /**
     * Returns the current optimistic locking version.
     *
     * @return the current optimistic locking version
     */
    public long getVersion() { return version; }
}
