package ru.vehxi.taskflow.bootstrap;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vehxi.taskflow.security.DatabaseUserDetailsService;
import ru.vehxi.taskflow.users.domain.*;
import ru.vehxi.taskflow.users.repository.*;
/** Creates the first administrator without overwriting existing accounts. */
@Service
public class BootstrapAdminService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final Validator validator;
    /**
     * Creates the initialization service.
     *
     * @param users account persistence
     *
     * @param roles role persistence
     *
     * @param encoder password encoder
     *
     * @param jdbc connection used for the transaction-scoped initialization lock
     *
     * @param validator input validator
     */
    public BootstrapAdminService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
            JdbcTemplate jdbc, Validator validator) {
        this.users = users; this.roles = roles; this.encoder = encoder; this.jdbc = jdbc; this.validator = validator;
    }
    /**
     * Initializes the component with the supplied parameters.
     *
     * @param properties externally supplied administrator credentials
     */
    @Transactional
    public void initialize(BootstrapProperties properties) {
        if (!properties.enabled()) { return; }
        jdbc.execute("SELECT pg_advisory_xact_lock(84621, 1)");
        if (users.existsByRoles_Code(RoleCode.ADMIN)) { return; }
        var input = new Credentials(properties.name().strip(),
                DatabaseUserDetailsService.normalizeEmail(properties.email()), properties.password());
        if (!validator.validate(input).isEmpty() || input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Set a valid bootstrap name, email and a nonblank password of at most 72 UTF-8 bytes");
        }
        if (users.existsByEmail(input.email())) {
            throw new IllegalStateException("Bootstrap email is already registered; existing account was not changed");
        }
        var admin = roles.findById(RoleCode.ADMIN).orElseThrow(() -> new IllegalStateException("ADMIN role missing"));
        users.saveAndFlush(new User(input.name(), input.email(), encoder.encode(input.password()), Set.of(admin)));
    }
    private record Credentials(@NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "Credentials[REDACTED]"; }
    }
}
