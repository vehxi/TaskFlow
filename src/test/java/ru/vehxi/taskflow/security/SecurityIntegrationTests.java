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

    @Test
    void projectsUseOwnershipAndMembershipForListAndDirectAccess() throws Exception {
        long manager = createUser("owner@example.test", "MANAGER");
        long otherManager = createUser("other-manager@example.test", "MANAGER");
        long executor = createUser("executor@example.test", "EXECUTOR");
        createUser("admin@example.test", "ADMIN");
        var ownerSession = login("owner@example.test");
        var otherSession = login("other-manager@example.test");
        var executorSession = login("executor@example.test");
        var adminSession = login("admin@example.test");

        String project = createProject(ownerSession, manager, "  Release plan  ");
        createProject(otherSession, otherManager, "Other plan");
        mvc.perform(get("/api/projects").session(ownerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Release plan"));
        mvc.perform(get("/api/projects").session(executorSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(project).session(executorSession)).andExpect(status().isNotFound());
        mvc.perform(get(project).session(otherSession)).andExpect(status().isNotFound());
        mvc.perform(get(project).session(adminSession)).andExpect(status().isForbidden());
        mvc.perform(get(project + "/members").session(otherSession)).andExpect(status().isNotFound());
        mvc.perform(get("/api/assignable-users").session(executorSession)).andExpect(status().isForbidden());
        mvc.perform(put(project).session(otherSession).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Foreign edit\",\"expectedVersion\":0}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/projects").session(executorSession).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Forbidden project\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/assignable-users").session(ownerSession).param("projectId", project.substring(project.lastIndexOf('/') + 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(executor))
                .andExpect(jsonPath("$.items[0].email").doesNotExist());

        addMember(ownerSession, project, executor, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectVersion").value(1));
        mvc.perform(get("/api/projects").session(executorSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(project).session(executorSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Release plan")).andExpect(jsonPath("$.tasks").doesNotExist());
        mvc.perform(get(project + "/members").session(executorSession)).andExpect(status().isForbidden());
        mvc.perform(get(project + "/members").session(ownerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectVersion").value(1))
                .andExpect(jsonPath("$.items[0].id").value(executor)).andExpect(jsonPath("$.items[0].email").doesNotExist());

        mvc.perform(delete(project + "/members/" + executor).session(ownerSession).with(csrf())
                .param("expectedProjectVersion", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectVersion").value(2));
        mvc.perform(get(project).session(executorSession)).andExpect(status().isNotFound());
    }

    @Test
    void projectCommandsCheckVersionsValidationAndOpenTaskAssignments() throws Exception {
        long manager = createUser("owner@example.test", "MANAGER");
        long executor = createUser("executor@example.test", "EXECUTOR");
        long inactive = createUser("inactive@example.test", "EXECUTOR");
        jdbc.update("UPDATE users SET active = false WHERE id = ?", inactive);
        var ownerSession = login("owner@example.test");

        mvc.perform(post("/api/projects").session(ownerSession).with(csrf()).contentType("application/json")
                .content("{\"name\":\"  \"}")).andExpect(status().isBadRequest());
        String project = createProject(ownerSession, manager, "Initial plan");
        long projectId = Long.parseLong(project.substring(project.lastIndexOf('/') + 1));
        mvc.perform(put(project).session(ownerSession).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Updated plan\",\"description\":\"New details\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(put(project).session(ownerSession).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Updated plan\",\"description\":\"New details\",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(put(project).session(ownerSession).with(csrf()).contentType("application/json")
                .content("{\"name\":\"No version\"}")).andExpect(status().isBadRequest());
        addMember(ownerSession, project, executor, 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        addMember(ownerSession, project, inactive, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RULE_CONFLICT"));
        addMember(ownerSession, project, manager, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RULE_CONFLICT"));
        addMember(ownerSession, project, executor, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectVersion").value(2));
        addMember(ownerSession, project, executor, 2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"));
        mvc.perform(get("/api/assignable-users").session(ownerSession).param("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        String suffix = java.util.UUID.randomUUID().toString();
        long type = jdbc.queryForObject("INSERT INTO task_types (name) VALUES (?) RETURNING id", Long.class, "Type " + suffix);
        long priority = jdbc.queryForObject("INSERT INTO task_priorities (name) VALUES (?) RETURNING id", Long.class, "Priority " + suffix);
        long task = jdbc.queryForObject("""
                INSERT INTO tasks (project_id, author_id, assignee_id, type_id, priority_id, title, due_date)
                VALUES (?, ?, ?, ?, ?, 'Open task', CURRENT_DATE) RETURNING id
                """, Long.class, projectId, manager, executor, type, priority);
        String membership = project + "/members/" + executor;
        mvc.perform(delete(membership).session(ownerSession).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete(membership).session(ownerSession).with(csrf()).param("expectedProjectVersion", "2"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RULE_CONFLICT"));
        mvc.perform(get(project).session(ownerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated plan")).andExpect(jsonPath("$.version").value(2));
        jdbc.update("UPDATE tasks SET status = 'DONE' WHERE id = ?", task);
        mvc.perform(delete(membership).session(ownerSession).with(csrf()).param("expectedProjectVersion", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.projectVersion").value(3));
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM tasks WHERE id = ?", Long.class, task)).isEqualTo(executor);
    }

    @Test
    void taskAssignmentAndFiltersFollowTheCurrentExecutorAndProjectOwner() throws Exception {
        long owner = createUser("owner@example.test", "MANAGER");
        long otherManager = createUser("other-manager@example.test", "MANAGER");
        long first = createUser("first@example.test", "EXECUTOR");
        long second = createUser("second@example.test", "EXECUTOR");
        var manager = login("owner@example.test");
        var foreign = login("other-manager@example.test");
        var firstSession = login("first@example.test");
        var secondSession = login("second@example.test");
        String project = createProject(manager, owner, "Owner project");
        String foreignProject = createProject(foreign, otherManager, "Foreign project");
        addMember(manager, project, first, 0).andExpect(status().isOk());
        addMember(manager, project, second, 1).andExpect(status().isOk());
        addMember(foreign, foreignProject, first, 0).andExpect(status().isOk());
        long type = referenceId("task_types", "Задача");
        long priority = referenceId("task_priorities", "Обычный");
        String command = taskCommand(first, "2026-12-09", type, priority);
        String task = mvc.perform(post(project + "/tasks").session(manager).with(csrf())
                        .contentType("application/json").content(command))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.authorId").value(owner))
                .andExpect(jsonPath("$.data.status").value("NEW")).andExpect(jsonPath("$.data.dueDate").value("2026-12-09"))
                .andExpect(jsonPath("$.data.version").value(0)).andReturn().getResponse().getHeader("Location");
        mvc.perform(post(foreignProject + "/tasks").session(foreign).with(csrf()).contentType("application/json").content(command))
                .andExpect(status().isCreated());
        mvc.perform(get(task).session(firstSession)).andExpect(status().isOk());
        mvc.perform(get(task).session(secondSession)).andExpect(status().isNotFound());
        mvc.perform(get(task).session(foreign)).andExpect(status().isNotFound());
        mvc.perform(put(task).session(firstSession).with(csrf()).contentType("application/json")
                .content(withVersion(command, 0))).andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks").session(manager).param("status", "NEW").param("dueFrom", "2026-12-08")
                .param("dueTo", "2026-12-09").param("assigneeId", String.valueOf(first)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].projectName").value("Owner project"));
        mvc.perform(get("/api/tasks").session(manager).param("dueFrom", "2026-12-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/tasks").session(firstSession).param("assigneeId", String.valueOf(second)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks/filter-options").session(firstSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projects.length()").value(2))
                .andExpect(jsonPath("$.assignees.length()").value(0));
        mvc.perform(get("/api/tasks/filter-options").session(manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.assignees[0].id").value(first));

        String replacement = withVersion(taskCommand(second, "2026-12-10", type, priority), 0);
        mvc.perform(put(task).session(manager).with(csrf()).contentType("application/json").content(replacement))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assigneeId").value(second))
                .andExpect(jsonPath("$.data.dueDate").value("2026-12-10")).andExpect(jsonPath("$.data.authorId").value(owner))
                .andExpect(jsonPath("$.data.status").value("NEW")).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(put(task).session(manager).with(csrf()).contentType("application/json").content(replacement))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(get(task).session(firstSession)).andExpect(status().isNotFound());
        mvc.perform(get(task).session(secondSession)).andExpect(status().isOk());
        mvc.perform(get("/api/tasks").session(firstSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].projectName").value("Foreign project"));
    }

    @Test
    void taskCommandsRejectInvalidDatesInactiveReferencesAndIneligibleAssignees() throws Exception {
        long owner = createUser("owner@example.test", "MANAGER");
        long executor = createUser("executor@example.test", "EXECUTOR");
        long outsider = createUser("outsider@example.test", "EXECUTOR");
        var manager = login("owner@example.test");
        String project = createProject(manager, owner, "Assignment checks");
        addMember(manager, project, executor, 0).andExpect(status().isOk());
        long type = jdbc.queryForObject("INSERT INTO task_types (name) VALUES ('Test type') RETURNING id", Long.class);
        long priority = referenceId("task_priorities", "Обычный");
        for (String date : java.util.List.of("2026-02-30", "0000-02-01", "26-12-09")) {
            mvc.perform(post(project + "/tasks").session(manager).with(csrf()).contentType("application/json")
                    .content(taskCommand(executor, date, type, priority))).andExpect(status().isBadRequest());
        }
        mvc.perform(post(project + "/tasks").session(manager).with(csrf()).contentType("application/json")
                .content(taskCommand(outsider, "2026-12-09", type, priority))).andExpect(status().isConflict());
        mvc.perform(get("/api/tasks").session(manager).param("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks").session(manager).param("dueFrom", "2026-12-10").param("dueTo", "2026-12-09"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks", Long.class)).isZero();

        String command = taskCommand(executor, "2026-12-09", type, priority);
        String task = mvc.perform(post(project + "/tasks").session(manager).with(csrf()).contentType("application/json").content(command))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        jdbc.update("UPDATE task_types SET active = false WHERE id = ?", type);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", executor);
        mvc.perform(get(project + "/members").session(manager).param("assignableOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post(project + "/tasks").session(manager).with(csrf()).contentType("application/json")
                .content(command)).andExpect(status().isConflict());
        mvc.perform(put(task).session(manager).with(csrf()).contentType("application/json")
                .content(withVersion(taskCommand(executor, "2026-12-10", type, priority), 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        jdbc.update("UPDATE tasks SET status = 'DONE'");
        mvc.perform(put(task).session(manager).with(csrf()).contentType("application/json")
                .content(withVersion(command, 1))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks", Long.class)).isEqualTo(1L);
    }

    @Test
    void taskHistorySurvivesReassignmentAndClosedTasksRemainReadable() throws Exception {
        long owner = createUser("owner@example.test", "MANAGER");
        long first = createUser("first@example.test", "EXECUTOR");
        long second = createUser("second@example.test", "EXECUTOR");
        var manager = login("owner@example.test");
        var executor = login("first@example.test");
        var replacement = login("second@example.test");
        String project = createProject(manager, owner, "Task activity");
        addMember(manager, project, first, 0).andExpect(status().isOk());
        addMember(manager, project, second, 1).andExpect(status().isOk());
        long type = referenceId("task_types", "Задача");
        long priority = referenceId("task_priorities", "Обычный");
        String task = postJson(manager, project + "/tasks", taskCommand(first, "2026-12-09", type, priority))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");

        postJson(executor, task + "/status", statusCommand("CANCELLED", 0)).andExpect(status().isForbidden());
        postJson(executor, task + "/status", statusCommand("DONE", 0)).andExpect(status().isConflict());
        postJson(executor, task + "/status", withAssignee(statusCommand("NEW", 0), second))
                .andExpect(status().isBadRequest());
        postJson(executor, task + "/status", statusCommand("NEW", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(0));
        postJson(executor, task + "/status", statusCommand("IN_PROGRESS", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));

        String comment = postJson(executor, task + "/comments", """
                {"body":"  Original note  ","expectedTaskVersion":1}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.data.comment.authorId").value(first))
                .andExpect(jsonPath("$.data.comment.body").value("Original note"))
                .andExpect(jsonPath("$.data.taskVersion").value(2)).andReturn().getResponse().getHeader("Location");
        postJson(executor, task + "/time-entries", timeCommand("2099-01-01", "30", 2)).andExpect(status().isBadRequest());
        postJson(executor, task + "/time-entries", timeCommand("2026-01-04", "2.5", 2)).andExpect(status().isBadRequest());
        postJson(executor, task + "/time-entries", timeCommand("2026-01-04", "0", 2)).andExpect(status().isBadRequest());
        postJson(manager, task + "/time-entries", timeCommand("2026-01-04", "30", 2)).andExpect(status().isForbidden());
        String entry = postJson(executor, task + "/time-entries", timeCommand("2026-01-04", "30", 2))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.timeEntry.authorId").value(first))
                .andExpect(jsonPath("$.data.taskVersion").value(3)).andReturn().getResponse().getHeader("Location");
        postJson(executor, task + "/status", statusCommand("DONE", 2))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));

        mvc.perform(put(task).session(manager).with(csrf()).contentType("application/json")
                        .content(withVersion(taskCommand(second, "2026-12-09", type, priority), 3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.version").value(4));
        mvc.perform(get(comment).session(executor)).andExpect(status().isNotFound());
        mvc.perform(get(comment).session(replacement)).andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(first));
        mvc.perform(get(entry).session(replacement)).andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(first));
        postJson(replacement, task + "/time-entries", timeCommand("2026-01-05", "45", 4))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.timeEntry.authorId").value(second))
                .andExpect(jsonPath("$.data.taskVersion").value(5));
        postJson(replacement, task + "/status", statusCommand("DONE", 5)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE")).andExpect(jsonPath("$.data.version").value(6));
        postJson(replacement, task + "/comments", """
                {"body":"Closed task comment","expectedTaskVersion":6}
                """).andExpect(status().isConflict());
        postJson(replacement, task + "/time-entries", timeCommand("2026-01-05", "10", 6))
                .andExpect(status().isConflict());
        mvc.perform(get(task + "/time-entries").param("size", "1").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalMinutes").value(75))
                .andExpect(jsonPath("$.items[0].authorId").value(first));
        mvc.perform(get(task + "/comments").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void reopeningCanRepairTheAssignmentAndDoesNotModifyHistory() throws Exception {
        long owner = createUser("owner@example.test", "MANAGER");
        long first = createUser("first@example.test", "EXECUTOR");
        long second = createUser("second@example.test", "EXECUTOR");
        var manager = login("owner@example.test");
        var executor = login("first@example.test");
        String project = createProject(manager, owner, "Reopen assignments");
        addMember(manager, project, first, 0).andExpect(status().isOk());
        addMember(manager, project, second, 1).andExpect(status().isOk());
        long type = referenceId("task_types", "Задача");
        long priority = referenceId("task_priorities", "Обычный");
        String task = postJson(manager, project + "/tasks", taskCommand(first, "2026-12-09", type, priority))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        String comment = postJson(manager, task + "/comments", """
                {"body":"Manager note","expectedTaskVersion":0}
                """).andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        postJson(manager, task + "/status", statusCommand("CANCELLED", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        postJson(executor, task + "/status", statusCommand("NEW", 2)).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET active = false WHERE id = ?", first);
        postJson(manager, task + "/status", statusCommand("NEW", 2)).andExpect(status().isConflict());
        postJson(manager, task + "/status", withAssignee(statusCommand("NEW", 2), second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assigneeId").value(second))
                .andExpect(jsonPath("$.data.status").value("NEW")).andExpect(jsonPath("$.data.version").value(3));
        var replacement = login("second@example.test");
        mvc.perform(get(comment).session(replacement)).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorId").value(owner));
        postJson(replacement, task + "/status", statusCommand("IN_PROGRESS", 3)).andExpect(status().isOk());
        postJson(replacement, task + "/status", statusCommand("DONE", 4)).andExpect(status().isOk());
        mvc.perform(get(task + "/time-entries").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(MockHttpSession session, String path, String body)
            throws Exception {
        return mvc.perform(post(path).session(session).with(csrf()).contentType("application/json").content(body));
    }

    private static String statusCommand(String target, long version) {
        return "{\"targetStatus\":\"" + target + "\",\"expectedVersion\":" + version + "}";
    }

    private static String withAssignee(String command, long assignee) {
        return command.substring(0, command.length() - 1) + ",\"assigneeId\":" + assignee + "}";
    }

    private static String timeCommand(String date, String minutes, long version) {
        return "{\"workDate\":\"" + date + "\",\"minutes\":" + minutes + ",\"expectedTaskVersion\":" + version + "}";
    }

    private long referenceId(String table, String name) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE name = ?", Long.class, name);
    }

    private static String taskCommand(long executor, String date, long type, long priority) {
        return """
                {"title":"Release task","description":"Prepare review","assigneeId":%d,"dueDate":"%s","typeId":%d,"priorityId":%d}
                """.formatted(executor, date, type, priority).strip();
    }

    private static String withVersion(String command, long version) {
        return command.substring(0, command.length() - 1) + ",\"expectedVersion\":" + version + "}";
    }

    private String createProject(MockHttpSession session, long manager, String name) throws Exception {
        return mvc.perform(post("/api/projects").session(session).with(csrf()).contentType("application/json")
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.managerId").value(manager))
                .andExpect(jsonPath("$.data.version").value(0)).andReturn().getResponse().getHeader("Location");
    }

    private org.springframework.test.web.servlet.ResultActions addMember(MockHttpSession session, String project,
            long userId, long version) throws Exception {
        return mvc.perform(post(project + "/members").session(session).with(csrf()).contentType("application/json")
                .content("{\"userId\":" + userId + ",\"expectedProjectVersion\":" + version + "}"));
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
