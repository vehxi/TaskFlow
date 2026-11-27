package ru.vehxi.taskflow.security;

import java.io.ByteArrayInputStream;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import static org.hamcrest.Matchers.hasItem;
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
    void administersBothReferenceCatalogsWithoutRemovingHistoricalValues() throws Exception {
        createUser("reference-admin@example.test", "ADMIN");
        createUser("reference-executor@example.test", "EXECUTOR");
        var admin = login("reference-admin@example.test");
        var executor = login("reference-executor@example.test");

        for (String catalog : java.util.List.of("/api/task-types", "/api/task-priorities")) {
            mvc.perform(post(catalog).session(executor).with(csrf()).contentType("application/json")
                    .content("""
                            {"name":"Для проверки"}
                            """)).andExpect(status().isForbidden());
            mvc.perform(post(catalog).session(admin).contentType("application/json")
                    .content("""
                            {"name":"Для проверки"}
                            """)).andExpect(status().isForbidden());
            mvc.perform(post(catalog).session(admin).with(csrf()).contentType("application/json")
                    .content("""
                            {"name":"   "}
                            """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").isArray());

            String location = mvc.perform(post(catalog).session(admin).with(csrf()).contentType("application/json")
                    .content("""
                            {"name":"  Для проверки  "}
                            """)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("Для проверки"))
                    .andExpect(jsonPath("$.data.active").value(true)).andExpect(jsonPath("$.data.version").value(0))
                    .andReturn().getResponse().getHeader("Location");
            mvc.perform(post(catalog).session(admin).with(csrf()).contentType("application/json")
                    .content("""
                            {"name":"  ДЛЯ ПРОВЕРКИ  "}
                            """)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"));
            mvc.perform(put(location).session(admin).with(csrf()).contentType("application/json")
                    .content("""
                            {"name":"Для проверки","active":false}
                            """)).andExpect(status().isBadRequest());
            String renameAndDeactivate = """
                    {"name":"Архивное значение","active":false,"expectedVersion":0}
                    """;
            mvc.perform(put(location).session(executor).with(csrf()).contentType("application/json")
                    .content(renameAndDeactivate)).andExpect(status().isForbidden());
            mvc.perform(put(location).session(admin).with(csrf()).contentType("application/json")
                    .content(renameAndDeactivate)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Архивное значение"))
                    .andExpect(jsonPath("$.data.active").value(false)).andExpect(jsonPath("$.data.version").value(1));
            mvc.perform(put(location).session(admin).with(csrf()).contentType("application/json")
                    .content(renameAndDeactivate)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
            mvc.perform(put(location).session(admin).with(csrf()).contentType("application/json")
                    .content("""
                            {"name":"Архивное значение","active":false,"expectedVersion":1}
                            """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
            mvc.perform(get(location).session(admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
        }

        mvc.perform(get("/api/reference-data").session(executor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.types[*].name").value(hasItem("Архивное значение")))
                .andExpect(jsonPath("$.priorities[*].name").value(hasItem("Архивное значение")));
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
        mvc.perform(post(project + "/tasks").session(manager).with(csrf()).contentType("application/json")
                .content(command)).andExpect(status().isConflict());
        mvc.perform(get(task).session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.typeId").value(type)).andExpect(jsonPath("$.typeName").value("Test type"));
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

    @Test
    void reportsOwnProjectProgressOverdueAndHistoricalTimeAndExportsTheSameTotals() throws Exception {
        long owner = createUser("report-owner@example.test", "MANAGER");
        long guest = createUser("report-other@example.test", "MANAGER");
        long first = createUser("report-first@example.test", "EXECUTOR");
        long second = createUser("report-second@example.test", "EXECUTOR");
        jdbc.update("UPDATE users SET name = 'Первый' WHERE id = ?", first);
        jdbc.update("UPDATE users SET name = '=Второй' WHERE id = ?", second);
        var manager = login("report-owner@example.test");
        var otherManager = login("report-other@example.test");
        var executor = login("report-first@example.test");
        String project = createProject(manager, owner, "=Проект отчёта");
        String otherProject = createProject(otherManager, guest, "Чужой проект");
        addMember(manager, project, first, 0).andExpect(status().isOk());
        addMember(manager, project, second, 1).andExpect(status().isOk());
        String report = project + "/report";
        String excel = report + ".xlsx";

        for (String path : java.util.List.of(report, excel, report + "/overdue")) {
            mvc.perform(get(path).session(executor)).andExpect(status().isForbidden());
            mvc.perform(get(path).session(otherManager)).andExpect(status().isNotFound());
        }
        mvc.perform(get(report)).andExpect(status().isUnauthorized());
        mvc.perform(get(report + "/overdue").session(manager).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get(report).session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.NEW").value(0)).andExpect(jsonPath("$.byStatus.DONE").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0.0)).andExpect(jsonPath("$.overdueCount").value(0))
                .andExpect(jsonPath("$.totalMinutes").value(0)).andExpect(jsonPath("$.tasksByAssignee").isEmpty())
                .andExpect(jsonPath("$.minutesByAuthor").isEmpty());

        var zone = ZoneId.of("Europe/Moscow");
        var today = LocalDate.now(zone);
        long projectId = Long.parseLong(project.substring(project.lastIndexOf('/') + 1));
        long otherId = Long.parseLong(otherProject.substring(otherProject.lastIndexOf('/') + 1));
        long oldest = reportTask(projectId, owner, second, "Просрочена", "NEW", today.minusDays(1));
        reportTask(projectId, owner, first, "Срок сегодня", "IN_PROGRESS", today);
        reportTask(projectId, owner, first, "Готова", "DONE", today.minusDays(1));
        reportTask(projectId, owner, second, "Тоже готова", "DONE", today.minusDays(1));
        long cancelled = reportTask(projectId, owner, first, "Отменена", "CANCELLED", today.minusDays(2));
        reportTask(otherId, guest, first, "Чужая задача", "NEW", today.minusDays(1));

        jdbc.update("""
                INSERT INTO time_entries (task_id, author_id, work_date, minutes)
                VALUES (?, ?, ?, 30), (?, ?, ?, 45), (?, ?, ?, 15)
                """, oldest, first, today, oldest, first, today, cancelled, second, today);
        jdbc.update("INSERT INTO comments (task_id, author_id, body) VALUES (?, ?, 'a'), (?, ?, 'b')",
                oldest, first, oldest, second);

        var result = mvc.perform(get(report).session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.project.name").value("=Проект отчёта"))
                .andExpect(jsonPath("$.byStatus.NEW").value(1)).andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.byStatus.DONE").value(2)).andExpect(jsonPath("$.byStatus.CANCELLED").value(1))
                .andExpect(jsonPath("$.progressPercent").value(50.0)).andExpect(jsonPath("$.overdueCount").value(1))
                .andExpect(jsonPath("$.totalMinutes").value(90)).andReturn().getResponse().getContentAsByteArray();
        var json = new tools.jackson.databind.json.JsonMapper().readTree(result);
        assertThat(OffsetDateTime.parse(json.path("generatedAt").asString()).getOffset())
                .isEqualTo(zone.getRules().getOffset(java.time.Instant.now()));
        for (var row : json.path("tasksByAssignee")) {
            long assigneeId = row.path("userId").asLong();
            assertThat(assigneeId).isIn(first, second);
            assertThat(row.path("taskCount").asLong()).isEqualTo(assigneeId == first ? 3 : 2);
        }
        assertThat(json.path("tasksByAssignee").size()).isEqualTo(2);
        for (var row : json.path("minutesByAuthor")) {
            long authorId = row.path("userId").asLong();
            assertThat(authorId).isIn(first, second);
            assertThat(row.path("minutes").asLong()).isEqualTo(authorId == first ? 75 : 15);
        }
        assertThat(json.path("minutesByAuthor").size()).isEqualTo(2);

        mvc.perform(get(report + "/overdue").session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(oldest))
                .andExpect(jsonPath("$.items[0].assigneeName").value("=Второй"));
        mvc.perform(get(report + "/overdue").session(manager).param("page", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items").isEmpty());

        String xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        byte[] bytes = mvc.perform(get(excel).session(manager).accept(xlsx))
                .andExpect(status().isOk()).andExpect(content().contentType(xlsx))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"taskflow-project-" + projectId + ".xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var projectCell = sheet.getRow(1).getCell(1);
            assertThat(projectCell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(projectCell.getStringCellValue()).isEqualTo("=Проект отчёта");
            assertThat(sheet.getRow(2).getCell(1).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(today);
            var progress = sheet.getRow(4).getCell(1);
            assertThat(progress.getNumericCellValue()).isEqualTo(0.5);
            assertThat(progress.getCellStyle().getDataFormatString()).isEqualTo("0.0%");
            assertThat(sheet.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(6).getCell(1).getNumericCellValue()).isEqualTo(90);
            assertThat(sheet.getRow(11).getCell(1).getNumericCellValue()).isEqualTo(2);
            var assignees = new java.util.HashMap<String, Double>();
            var authors = new java.util.HashMap<String, Double>();
            for (int row : new int[]{15, 16}) {
                assignees.put(sheet.getRow(row).getCell(0).getStringCellValue(), sheet.getRow(row).getCell(1).getNumericCellValue());
            }
            for (int row : new int[]{19, 20}) {
                authors.put(sheet.getRow(row).getCell(0).getStringCellValue(), sheet.getRow(row).getCell(1).getNumericCellValue());
            }
            assertThat(assignees).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("Первый", 3.0, "=Второй", 2.0));
            assertThat(authors).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("Первый", 75.0, "=Второй", 15.0));
        }
    }

    private long reportTask(long projectId, long owner, long assignee, String title, String status, LocalDate deadline) {
        return jdbc.queryForObject("""
                INSERT INTO tasks (project_id, author_id, assignee_id, type_id, priority_id, title, status, due_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, projectId, owner, assignee, referenceId("task_types", "Задача"),
                referenceId("task_priorities", "Обычный"), title, status, deadline);
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
