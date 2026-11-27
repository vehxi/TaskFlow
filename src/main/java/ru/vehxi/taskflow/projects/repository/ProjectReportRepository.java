package ru.vehxi.taskflow.projects.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.vehxi.taskflow.projects.web.ProjectReport.AssigneeTasks;
import ru.vehxi.taskflow.projects.web.ProjectReport.AuthorMinutes;
import ru.vehxi.taskflow.projects.web.ProjectReport.OverdueTask;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;

/** Project-scoped report queries. Callers verify ownership before reading. */
@Repository
public class ProjectReportRepository {
    private static final String OVERDUE = "t.status in ('NEW', 'IN_PROGRESS') and t.due_date < :today";
    private final JdbcClient jdbc;

    /** @param jdbc database query client */
    public ProjectReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param projectId project identity
     * @param today calendar date in the application zone
     * @return counts for statuses present in the project */
    public List<StatusCount> byStatus(Long projectId, LocalDate today) {
        String sql = """
                select t.status, count(*) as task_count, count(*) filter (where %s) as overdue_count
                from tasks t
                where t.project_id = :projectId
                group by t.status
                """.formatted(OVERDUE);
        return jdbc.sql(sql).param("projectId", projectId).param("today", today)
                .query((row, index) -> new StatusCount(TaskStatus.valueOf(row.getString("status")),
                        row.getLong("task_count"), row.getLong("overdue_count"))).list();
    }

    /** @param projectId project identity
     * @return counts by current assignee */
    public List<AssigneeTasks> byAssignee(Long projectId) {
        return jdbc.sql("""
                select u.id, u.name, count(*) as task_count
                from tasks t join users u on u.id = t.assignee_id
                where t.project_id = :projectId
                group by u.id, u.name
                order by u.name, u.id
                """).param("projectId", projectId)
                .query((row, index) -> new AssigneeTasks(row.getLong("id"), row.getString("name"),
                        row.getLong("task_count"))).list();
    }

    /** @param projectId project identity
     * @return work minutes by entry author, including work on cancelled tasks */
    public List<AuthorMinutes> byAuthor(Long projectId) {
        return jdbc.sql("""
                select u.id, u.name, sum(e.minutes) as minutes
                from time_entries e join tasks t on t.id = e.task_id
                join users u on u.id = e.author_id
                where t.project_id = :projectId
                group by u.id, u.name
                order by u.name, u.id
                """).param("projectId", projectId)
                .query((row, index) -> new AuthorMinutes(row.getLong("id"), row.getString("name"),
                        row.getLong("minutes"))).list();
    }

    /** @param projectId project identity
     * @param today calendar date in the application zone
     * @param page requested page
     * @return overdue tasks ordered by deadline and identity */
    public Page<OverdueTask> overdue(Long projectId, LocalDate today, Pageable page) {
        String filter = " where t.project_id = :projectId and " + OVERDUE;
        long total = jdbc.sql("select count(*) from tasks t" + filter)
                .param("projectId", projectId).param("today", today).query(Long.class).single();
        if (page.getOffset() >= total) {
            return new PageImpl<>(List.of(), page, total);
        }
        var items = jdbc.sql("""
                select t.id, t.title, t.due_date, t.status, u.name as assignee_name
                from tasks t join users u on u.id = t.assignee_id
                """ + filter + " order by t.due_date, t.id limit :limit offset :offset")
                .param("projectId", projectId).param("today", today)
                .param("limit", page.getPageSize()).param("offset", page.getOffset())
                .query((row, index) -> new OverdueTask(row.getLong("id"), row.getString("title"),
                        row.getObject("due_date", LocalDate.class), TaskStatus.valueOf(row.getString("status")),
                        row.getString("assignee_name"))).list();
        return new PageImpl<>(items, page, total);
    }

    /** @param status current task status
     * @param count tasks in that status
     * @param overdueCount overdue tasks in that status */
    public record StatusCount(TaskStatus status, long count, long overdueCount) { }
}
