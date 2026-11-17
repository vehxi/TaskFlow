package ru.vehxi.taskflow.common.domain;
import jakarta.persistence.*;
/** Persistent object identified by a database-generated key. */
@MappedSuperclass
public abstract class IdentityEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Initializes an entity for persistence. */
    protected IdentityEntity() { }
    /**
     * Returns the persistent key, or null before insertion.
     *
     * @return the persistent key, or null before insertion
     */
    public Long getId() { return id; }
}
