package ru.vehxi.taskflow;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"app.environment-file.enabled=false", "app.bootstrap.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class DatabaseMigrationTests {

    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:16.15")
            .withDatabaseName("task_tracker_test")
            .withUsername("task_tracker_migrator")
            .withPassword("integration-test-only")
            .withInitScript("db/create-application-role.sql");

    private final JdbcTemplate jdbc;
    private final Flyway flyway;

    @Autowired
    DatabaseMigrationTests(JdbcTemplate jdbc, Flyway flyway) {
        this.jdbc = jdbc;
        this.flyway = flyway;
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

    @Test
    void migratesAllDomainTablesAndSeedsFixedRoles() {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrder(
                "users", "roles", "user_roles", "projects", "project_members",
                "task_types", "task_priorities", "tasks", "comments", "time_entries");
        assertThat(jdbc.queryForList("SELECT code FROM roles", String.class))
                .containsExactlyInAnyOrder("ADMIN", "MANAGER", "EXECUTOR");
        assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("task_tracker_app");
    }

    @Test
    void migrationsCanRunAgainWithoutDuplicatingData() {
        flyway.migrate();
        assertThat(flyway.info().applied()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Long.class)).isEqualTo(3L);
    }

    @Test
    void applicationCannotDeleteTasksOrRewriteHistory() {
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, 'tasks', 'DELETE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, 'comments', 'UPDATE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, 'time_entries', 'DELETE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, 'project_members', 'DELETE')", Boolean.class)).isTrue();
    }

    @Test
    void preservesAssignmentWhenAClosedTaskParticipantLeaves() {
        Fixture fixture = createTask();
        jdbc.update("UPDATE tasks SET status = 'DONE' WHERE id = ?", fixture.taskId());
        jdbc.update("DELETE FROM project_members WHERE project_id = ? AND user_id = ?",
                fixture.projectId(), fixture.assigneeId());
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM tasks WHERE id = ?",
                Long.class, fixture.taskId())).isEqualTo(fixture.assigneeId());
    }

    @Test
    void mapsTasksRolesAndGeneratedTimestampsThroughJpa(
            @Autowired ru.vehxi.taskflow.tasks.repository.TaskRepository tasks,
            @Autowired ru.vehxi.taskflow.tasks.repository.TimeEntryRepository entries) {
        Fixture fixture = createTask();
        var task = tasks.findById(fixture.taskId()).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(ru.vehxi.taskflow.tasks.domain.TaskStatus.NEW);
        assertThat(task.getAssignee().getId()).isEqualTo(fixture.assigneeId());
        assertThat(task.getAssignee().getRoles()).extracting("code")
                .containsExactly(ru.vehxi.taskflow.users.domain.RoleCode.EXECUTOR);
        assertThat(task.getCreatedAt()).isNotNull();
        var entry = entries.saveAndFlush(new ru.vehxi.taskflow.tasks.domain.TimeEntry(
                task, task.getAssignee(), task.getDueDate(), 30));
        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getCreatedAt()).isNotNull();
        assertThat(entry.getAuthor().getId()).isEqualTo(fixture.assigneeId());
    }

    @Test
    void rejectsZeroMinutes() {
        Fixture fixture = createTask();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO time_entries (task_id, author_id, work_date, minutes)
                VALUES (?, ?, CURRENT_DATE, 0)
                """, fixture.taskId(), fixture.assigneeId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsTimeWithItsAuthorAfterReassignment() {
        Fixture fixture = createTask();
        long replacement = createUser("replacement@example.test");
        jdbc.update("INSERT INTO user_roles VALUES (?, 'EXECUTOR')", replacement);
        jdbc.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)",
                fixture.projectId(), replacement);
        jdbc.update("""
                INSERT INTO time_entries (task_id, author_id, work_date, minutes)
                VALUES (?, ?, CURRENT_DATE, 30), (?, ?, CURRENT_DATE, 45)
                """, fixture.taskId(), fixture.assigneeId(), fixture.taskId(), fixture.assigneeId());
        jdbc.update("UPDATE tasks SET assignee_id = ? WHERE id = ?", replacement, fixture.taskId());
        assertThat(jdbc.queryForObject("""
                SELECT sum(minutes) FROM time_entries WHERE task_id = ? AND author_id = ?
                """, Long.class, fixture.taskId(), fixture.assigneeId())).isEqualTo(75L);
    }

    @Test
    void rejectsCaseInsensitiveDictionaryDuplicates() {
        jdbc.update("INSERT INTO task_types (name) VALUES ('Ошибка')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO task_types (name) VALUES ('ОШИБКА')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long createUser(String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (name, email, password_hash)
                VALUES ('Test user', ?, 'test-hash-not-for-login') RETURNING id
                """, Long.class, email);
    }

    private Fixture createTask() {
        long manager = createUser("manager@example.test");
        long assignee = createUser("executor@example.test");
        jdbc.update("INSERT INTO user_roles VALUES (?, 'MANAGER'), (?, 'EXECUTOR')", manager, assignee);
        long project = jdbc.queryForObject("""
                INSERT INTO projects (manager_id, name) VALUES (?, 'Test project') RETURNING id
                """, Long.class, manager);
        jdbc.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)", project, assignee);
        long type = jdbc.queryForObject("INSERT INTO task_types (name) VALUES ('Task') RETURNING id", Long.class);
        long priority = jdbc.queryForObject("INSERT INTO task_priorities (name) VALUES ('Normal') RETURNING id", Long.class);
        long task = jdbc.queryForObject("""
                INSERT INTO tasks (project_id, author_id, assignee_id, type_id, priority_id, title, due_date)
                VALUES (?, ?, ?, ?, ?, 'Test task', CURRENT_DATE) RETURNING id
                """, Long.class, project, manager, assignee, type, priority);
        return new Fixture(task, project, assignee);
    }

    private record Fixture(long taskId, long projectId, long assigneeId) {
    }
}
