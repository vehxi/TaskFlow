package ru.vehxi.taskflow.security;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.vehxi.taskflow.users.domain.RoleCode;
/** Session identity containing a stable account key and current permissions. */
public final class TaskFlowPrincipal extends org.springframework.security.core.userdetails.User {
    private static final long serialVersionUID = 1L;
    /** Stable account identity. */
    private final Long id;
    /** Display name from the current account. */
    private final String displayName;
    /** Current permission snapshot. */
    private final Set<RoleCode> roles;
    /**
     * Initializes the component with the supplied parameters.
     *
     * @param account the account loaded with roles
     */
    public TaskFlowPrincipal(ru.vehxi.taskflow.users.domain.User account) {
        super(account.getEmail(), account.getPasswordHash(), account.isActive(), true, true, true,
                account.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.getCode().name())).toList());
        id = account.getId(); displayName = account.getName();
        roles = account.getRoles().stream().map(r -> r.getCode()).collect(Collectors.toUnmodifiableSet());
    }
    /**
     * Returns the stable account key.
     *
     * @return the stable account key
     */
    public Long getId() { return id; }
    /**
     * Returns the display name.
     *
     * @return the display name
     */
    public String getDisplayName() { return displayName; }
    /**
     * Returns the current role codes.
     *
     * @return the current role codes
     */
    public Set<RoleCode> getRoleCodes() { return roles; }
}
