package ru.vehxi.taskflow.projects.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;

/** A project summary from a single database snapshot.
 * @param generatedAt generation time in the application zone
 * @param project reported project
 * @param byStatus task counts including statuses with no tasks
 * @param overdueCount open tasks due before the report date
 * @param progressPercent closed-as-done share of non-cancelled tasks
 * @param totalMinutes minutes logged across all project tasks
 * @param tasksByAssignee task counts grouped by current assignee
 * @param minutesByAuthor logged minutes grouped by entry author */
public record ProjectReport(OffsetDateTime generatedAt, ProjectSummaryDto project, Map<TaskStatus, Long> byStatus,
        long overdueCount, BigDecimal progressPercent, long totalMinutes, List<AssigneeTasks> tasksByAssignee,
        List<AuthorMinutes> minutesByAuthor) {
    /** @param userId current assignee identity
     * @param name current assignee name
     * @param taskCount number of assigned tasks */
    public record AssigneeTasks(long userId, String name, long taskCount) { }

    /** @param userId time-entry author identity
     * @param name author name
     * @param minutes total logged minutes */
    public record AuthorMinutes(long userId, String name, long minutes) { }

    /** @param id task identity
     * @param title task title
     * @param dueDate task deadline
     * @param status current task status
     * @param assigneeName current assignee name */
    public record OverdueTask(long id, String title, LocalDate dueDate, TaskStatus status, String assigneeName) { }
}
