package ru.vehxi.taskflow.users.domain;
import jakarta.persistence.*;
/** A fixed application role seeded by Flyway. */
@Entity @Table(name = "roles")
public class Role {
    @Id @Enumerated(EnumType.STRING) @Column(length = 16)
    private RoleCode code;
    /** Initializes a role for persistence. */
    protected Role() { }
    /**
     * Returns the role code.
     *
     * @return the role code
     */
    public RoleCode getCode() { return code; }
}
