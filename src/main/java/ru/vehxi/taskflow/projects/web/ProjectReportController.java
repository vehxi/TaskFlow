package ru.vehxi.taskflow.projects.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vehxi.taskflow.common.web.PageResponse;
import ru.vehxi.taskflow.projects.service.ExcelExportService;
import ru.vehxi.taskflow.projects.service.ProjectReportService;
import ru.vehxi.taskflow.projects.web.ProjectReport.OverdueTask;
import ru.vehxi.taskflow.security.TaskFlowPrincipal;

/** Manager-only project reporting and export. */
@RestController
@RequestMapping("/api/projects/{id}")
public class ProjectReportController {
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ProjectReportService reports;
    private final ExcelExportService excel;

    /** @param reports report queries
     * @param excel workbook export */
    public ProjectReportController(ProjectReportService reports, ExcelExportService excel) {
        this.reports = reports;
        this.excel = excel;
    }

    /** @param actor current account
     * @param id reported project
     * @return current project report */
    @GetMapping("/report")
    public ResponseEntity<ProjectReport> get(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.get(actor.getId(), id));
    }

    /** @param actor current account
     * @param id reported project
     * @param page zero-based page number
     * @param size page capacity
     * @return overdue task page */
    @GetMapping("/report/overdue")
    public ResponseEntity<PageResponse<OverdueTask>> overdue(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reports.overdue(actor.getId(), id, page, size));
    }

    /** @param actor current account
     * @param id reported project
     * @return current report as an Excel attachment
     * @throws IOException if workbook generation fails */
    @GetMapping("/report.xlsx")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id) throws IOException {
        byte[] file = excel.export(reports.get(actor.getId(), id));
        var disposition = ContentDisposition.attachment().filename("taskflow-project-" + id + ".xlsx").build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(XLSX).contentLength(file.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(file);
    }
}
