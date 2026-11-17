package ru.vehxi.taskflow.security;

import java.sql.DriverManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.vehxi.taskflow.bootstrap.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.environment-file.enabled=false", "app.bootstrap.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(SecurityIntegrationTests.ProbeController.class)
class SecurityIntegrationTests {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:16.15")
            .withDatabaseName("taskflow_security_test")
            .withUsername("task_tracker_migrator").withPassword("integration-test-only")
            .withInitScript("db/create-application-role.sql");
    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final BootstrapAdminService bootstrap;

    @Autowired
    SecurityIntegrationTests(MockMvc mvc, JdbcTemplate jdbc, PasswordEncoder encoder, BootstrapAdminService bootstrap) {
        this.mvc = mvc; this.jdbc = jdbc; this.encoder = encoder; this.bootstrap = bootstrap;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "task_tracker_app");
        registry.add("spring.datasource.password", () -> "integration-test-only");
        registry.add("spring.flyway.url", DATABASE::getJdbcUrl);
        registry.add("spring.flyway.user", DATABASE::getUsername);
        registry.add("spring.flyway.password", DATABASE::getPassword);
        registry.add("spring.flyway.placeholders.applicationUser", () -> "task_tracker_app");
    }

    @BeforeEach
    void cleanTestAccounts() throws Exception {
        try (var connection = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("TRUNCATE users CASCADE");
        }
    }

    @Test
    void requiresAuthenticationAndProvidesCsrfToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void logsInWithEmailAndReturnsOnlyAccountDto() throws Exception {
        createUser("alice@example.test", "MANAGER");
        var session = login(" ALICE@EXAMPLE.TEST ");
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsInvalidPasswordAndMissingCsrf() throws Exception {
        createUser("alice@example.test", "MANAGER");
        mvc.perform(post("/api/session").with(csrf()).param("email", "alice@example.test").param("password", "incorrect"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/session").param("email", "alice@example.test").param("password", "correct-password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsBlockedAccountsAndInvalidatesOpenSessions() throws Exception {
        long id = createUser("alice@example.test", "MANAGER");
        var session = login("alice@example.test");
        jdbc.update("UPDATE users SET active = false, version = version + 1 WHERE id = ?", id);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(post("/api/session").with(csrf()).param("email", "alice@example.test").param("password", "correct-password"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void roleRevocationAffectsTheSameSession() throws Exception {
        long id = createUser("admin@example.test", "ADMIN");
        var session = login("admin@example.test");
        mvc.perform(get("/api/users/probe").session(session)).andExpect(status().isOk());
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", id);
        jdbc.update("INSERT INTO user_roles VALUES (?, 'EXECUTOR')", id);
        jdbc.update("UPDATE users SET version = version + 1 WHERE id = ?", id);
        mvc.perform(get("/api/users/probe").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("EXECUTOR"));
    }

    @Test
    void logoutRequiresCsrfAndClosesSession() throws Exception {
        createUser("alice@example.test", "EXECUTOR");
        var session = login("alice@example.test");
        mvc.perform(post("/api/session/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/session/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapIsExplicitAndIdempotent() {
        bootstrap.initialize(new BootstrapProperties(false, "Admin", "admin@example.test", "initial-password"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
        bootstrap.initialize(new BootstrapProperties(true, "Admin", "ADMIN@example.test", "initial-password"));
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'admin@example.test'", String.class);
        assertThat(encoder.matches("initial-password", hash)).isTrue();
        bootstrap.initialize(new BootstrapProperties(true, "Other", "other@example.test", "different-password"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users", String.class)).isEqualTo(hash);
    }

    @Test
    void bootstrapDoesNotPromoteOrOverwriteAnExistingEmail() {
        createUser("admin@example.test", "EXECUTOR");
        assertThatThrownBy(() -> bootstrap.initialize(new BootstrapProperties(true, "Admin", "admin@example.test", "initial-password")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already registered");
        assertThat(jdbc.queryForObject("SELECT role_code FROM user_roles", String.class)).isEqualTo("EXECUTOR");
    }

    @Test
    void bootstrapRejectsEmptyCredentials() {
        assertThatThrownBy(() -> bootstrap.initialize(new BootstrapProperties(true, "Admin", "", "")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
    }

    @Test
    void accountCommandsPersistRolesAndIncrementTheVersionExactlyOnce() throws Exception {
        createUser("admin@example.test", "ADMIN");
        var session = login("admin@example.test");
        var created = mvc.perform(post("/api/users").session(session).with(csrf()).contentType("application/json").content("""
                {"name":"Anna","email":"ANNA@example.test","initialPassword":"new-secret","roles":["EXECUTOR"]}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist()).andReturn();
        var json = new tools.jackson.databind.json.JsonMapper();
        long id = json.readTree(created.getResponse().getContentAsString()).get("data").get("id").asLong();
        String originalHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
        assertThat(encoder.matches("new-secret", originalHash)).isTrue();
        mvc.perform(put("/api/users/" + id).session(session).with(csrf()).contentType("application/json").content("""
                {"name":"Anna","email":"anna@example.test","roles":["MANAGER","EXECUTOR"],"active":true,"expectedVersion":0}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.roles.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id = ?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id = ?", Long.class, id)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id)).isEqualTo(originalHash);
        mvc.perform(put("/api/users/" + id).session(session).with(csrf()).contentType("application/json").content("""
                {"name":"Overwrite","email":"anna@example.test","roles":["ADMIN"],"active":false,"expectedVersion":0}
                """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, id)).isEqualTo("Anna");
        assertThat(jdbc.queryForObject("SELECT active FROM users WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void roleRevocationChecksRelatedRowsAndBlockingPreservesThem() throws Exception {
        createUser("admin@example.test", "ADMIN");
        long manager = createUser("manager@example.test", "MANAGER");
        long executor = createUser("executor@example.test", "EXECUTOR");
        long project = jdbc.queryForObject("INSERT INTO projects (name, manager_id) VALUES ('Test project', ?) RETURNING id", Long.class, manager);
        jdbc.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)", project, executor);
        String suffix = java.util.UUID.randomUUID().toString();
        long type = jdbc.queryForObject("INSERT INTO task_types (name) VALUES (?) RETURNING id", Long.class, "Type " + suffix);
        long priority = jdbc.queryForObject("INSERT INTO task_priorities (name) VALUES (?) RETURNING id", Long.class, "Priority " + suffix);
        jdbc.update("INSERT INTO tasks (project_id, author_id, assignee_id, type_id, priority_id, title, due_date) VALUES (?, ?, ?, ?, ?, 'Task', CURRENT_DATE)",
                project, manager, executor, type, priority);
        var session = login("admin@example.test");
        for (var entry : java.util.Map.of(manager, "EXECUTOR", executor, "MANAGER").entrySet()) {
            String email = entry.getKey().equals(manager) ? "manager@example.test" : "executor@example.test";
            String command = "{\"name\":\"Test user\",\"email\":\"" + email + "\",\"roles\":[\"" + entry.getValue()
                    + "\"],\"active\":true,\"expectedVersion\":0}";
            mvc.perform(put("/api/users/" + entry.getKey()).session(session).with(csrf()).contentType("application/json").content(command))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RULE_CONFLICT"));
            assertThat(jdbc.queryForObject("SELECT version FROM users WHERE id = ?", Long.class, entry.getKey())).isZero();
        }
        mvc.perform(put("/api/users/" + executor).session(session).with(csrf()).contentType("application/json").content("""
                {"name":"Test user","email":"executor@example.test","roles":["EXECUTOR"],"active":false,"expectedVersion":0}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE assignee_id = ?", Long.class, executor)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT role_code FROM user_roles WHERE user_id = ?", String.class, executor)).isEqualTo("EXECUTOR");
    }

    private long createUser(String email, String role) {
        long id = jdbc.queryForObject("INSERT INTO users (name, email, password_hash) VALUES ('Test user', ?, ?) RETURNING id",
                Long.class, email, encoder.encode("correct-password"));
        jdbc.update("INSERT INTO user_roles VALUES (?, ?)", id, role);
        return id;
    }

    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/session").with(csrf()).param("email", email).param("password", "correct-password"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    @TestComponent
    @RestController
    static class ProbeController {
        @GetMapping("/api/users/probe") String probe() { return "ok"; }
    }
}
