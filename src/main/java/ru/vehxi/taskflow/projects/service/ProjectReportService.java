package ru.vehxi.taskflow.projects.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.vehxi.taskflow.common.web.PageResponse;
import ru.vehxi.taskflow.projects.repository.ProjectReportRepository;
import ru.vehxi.taskflow.projects.web.ProjectReport;
import ru.vehxi.taskflow.projects.web.ProjectReport.AuthorMinutes;
import ru.vehxi.taskflow.projects.web.ProjectReport.OverdueTask;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;

/** Reads project reports from consistent database snapshots. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProjectReportService {
    private final ProjectService projects;
    private final ProjectReportRepository reports;
    private final ZoneId zone;

    /** @param projects project access checks
     * @param reports report queries
     * @param timeZone application calendar zone */
    public ProjectReportService(ProjectService projects, ProjectReportRepository reports,
            @Value("${app.time-zone}") String timeZone) {
        this.projects = projects;
        this.reports = reports;
        this.zone = ZoneId.of(timeZone);
    }

    /** @param actorId current account
     * @param projectId project identity
     * @return report for the owning manager */
    public ProjectReport get(Long actorId, Long projectId) {
        var project = projects.requireManagedProject(actorId, projectId);
        var generatedAt = OffsetDateTime.now(zone);
        var byStatus = new EnumMap<TaskStatus, Long>(TaskStatus.class);
        for (var status : TaskStatus.values()) byStatus.put(status, 0L);
        long overdueCount = 0;
        for (var row : reports.byStatus(projectId, generatedAt.toLocalDate())) {
            byStatus.put(row.status(), row.count());
            overdueCount += row.overdueCount();
        }

        long counted = byStatus.get(TaskStatus.NEW) + byStatus.get(TaskStatus.IN_PROGRESS) + byStatus.get(TaskStatus.DONE);
        BigDecimal progress = counted == 0 ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(byStatus.get(TaskStatus.DONE)).movePointRight(2)
                        .divide(BigDecimal.valueOf(counted), 1, RoundingMode.HALF_UP);

        var minutesByAuthor = reports.byAuthor(projectId);
        long totalMinutes = minutesByAuthor.stream().mapToLong(AuthorMinutes::minutes).sum();
        return new ProjectReport(generatedAt, project, Map.copyOf(byStatus), overdueCount, progress, totalMinutes,
                List.copyOf(reports.byAssignee(projectId)), List.copyOf(minutesByAuthor));
    }

    /** @param actorId current account
     * @param projectId project identity
     * @param page zero-based page
     * @param size page capacity
     * @return overdue tasks in the owning manager's project */
    public PageResponse<OverdueTask> overdue(Long actorId, Long projectId, int page, int size) {
        projects.requireManagedProject(actorId, projectId);
        return PageResponse.from(reports.overdue(projectId, LocalDate.now(zone), PageRequest.of(page, size)));
    }
}
