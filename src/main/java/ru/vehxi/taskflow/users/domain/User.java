package ru.vehxi.taskflow.users.domain;
import jakarta.persistence.*;
import java.util.*;
import ru.vehxi.taskflow.common.domain.VersionedCreatedEntity;
/** A user account and its assigned roles. */
@Entity @Table(name = "users")
public class User extends VersionedCreatedEntity {
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(nullable = false) private boolean active = true;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_code"))
    private Set<Role> roles = new HashSet<>();
    /** Initializes an account for persistence. */
    protected User() { }
    /**
     * Creates an active account with an already encoded password.
     *
     * @param name display name
     *
     * @param email normalized email address
     *
     * @param passwordHash encoded password
     *
     * @param roles nonempty role set
     */
    public User(String name, String email, String passwordHash, Set<Role> roles) {
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        if (roles.isEmpty()) { throw new IllegalArgumentException("At least one role is required"); }
        this.roles.addAll(roles);
    }
    /**
     * Returns the display name.
     *
     * @return the display name
     */
    public String getName() { return name; }
    /**
     * Returns the normalized email.
     *
     * @return the normalized email
     */
    public String getEmail() { return email; }
    /**
     * Returns the encoded password, for authentication only.
     *
     * @return the encoded password, for authentication only
     */
    public String getPasswordHash() { return passwordHash; }
    /**
     * Returns whether the account can authenticate.
     *
     * @return whether the account can authenticate
     */
    public boolean isActive() { return active; }
    /**
     * Returns an immutable snapshot of the assigned roles.
     *
     * @return an immutable snapshot of the assigned roles
     */
    public Set<Role> getRoles() { return Set.copyOf(roles); }
}
