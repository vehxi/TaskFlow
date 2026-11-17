package ru.vehxi.taskflow.security;
import java.util.Locale;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vehxi.taskflow.users.domain.User;
import ru.vehxi.taskflow.users.repository.UserRepository;
/** Loads authentication data from PostgreSQL rather than a generated account. */
@Service
@Transactional(readOnly = true)
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    /**
     * Initializes the component with the supplied parameters.
     *
     * @param users account persistence
     */
    public DatabaseUserDetailsService(UserRepository users) { this.users = users; }
    /** {@inheritDoc} */
    @Override public TaskFlowPrincipal loadUserByUsername(String email) {
        return principal(users.findWithRolesByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Account unavailable")));
    }
    /**
     * Returns a fresh account identity.
     *
     * @param id stable account key
     * @return a fresh account identity
     */
    public TaskFlowPrincipal loadCurrentUser(Long id) {
        return principal(users.findWithRolesById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Account unavailable")));
    }
    /**
     * Returns a normalized address.
     *
     * @param email entered email
     * @return a normalized address
     */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
    private TaskFlowPrincipal principal(User user) {
        if (user.getRoles().isEmpty()) { throw new UsernameNotFoundException("Account unavailable"); }
        return new TaskFlowPrincipal(user);
    }
}
