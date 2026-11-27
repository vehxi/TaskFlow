package ru.vehxi.taskflow.projects.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import ru.vehxi.taskflow.projects.web.ProjectReport;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;

/** Renders the saved report values in a one-sheet Excel workbook. */
@Service
public class ExcelExportService {
    /** @param report consistent project snapshot
     * @return an Excel workbook
     * @throws IOException if writing the workbook fails */
    public byte[] export(ProjectReport report) throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Отчёт");
            sheet.setColumnWidth(0, 52 * 256);
            sheet.setColumnWidth(1, 39 * 256);
            var heading = headingStyle(workbook);
            var wrapped = workbook.createCellStyle();
            wrapped.setWrapText(true);
            var dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy hh:mm"));
            var percent = workbook.createCellStyle();
            percent.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));

            header(sheet, 0, "Отчёт по проекту", "Значение", heading);
            text(sheet, 1, "Проект", report.project().name(), wrapped);
            var dateRow = sheet.createRow(2);
            dateRow.createCell(0).setCellValue("Сформирован (" + report.generatedAt().getOffset().getId() + ")");
            var dateCell = dateRow.createCell(1);
            dateCell.setCellValue(report.generatedAt().toLocalDateTime());
            dateCell.setCellStyle(dateTime);

            var progressRow = sheet.createRow(4);
            progressRow.createCell(0).setCellValue("Прогресс");
            var progressCell = progressRow.createCell(1);
            progressCell.setCellValue(report.progressPercent().movePointLeft(2).doubleValue());
            progressCell.setCellStyle(percent);
            number(sheet, 5, "Просроченные задачи", report.overdueCount());
            number(sheet, 6, "Трудозатраты, мин", report.totalMinutes());

            int index = 8;
            header(sheet, index++, "Статус", "Задачи", heading);
            for (var status : TaskStatus.values()) {
                number(sheet, index++, statusName(status), report.byStatus().get(status));
            }

            header(sheet, ++index, "Текущий исполнитель", "Задачи", heading);
            for (var assignee : report.tasksByAssignee()) {
                number(sheet, ++index, assignee.name(), assignee.taskCount(), wrapped);
            }

            index += 2;
            header(sheet, index, "Автор записи времени", "Минуты", heading);
            for (var author : report.minutesByAuthor()) {
                number(sheet, ++index, author.name(), author.minutes(), wrapped);
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static CellStyle headingStyle(Workbook workbook) {
        var font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        var style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void header(Sheet sheet, int index, String label, String value, CellStyle style) {
        var row = sheet.createRow(index);
        row.setHeightInPoints(21);
        var key = row.createCell(0);
        key.setCellValue(label);
        key.setCellStyle(style);
        var cell = row.createCell(1);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void text(Sheet sheet, int index, String label, String value, CellStyle style) {
        var row = sheet.createRow(index);
        row.setHeightInPoints(15 * Math.max(1, (value.length() + 27) / 28));
        row.createCell(0).setCellValue(label);
        var cell = row.createCell(1);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void number(Sheet sheet, int index, String label, long value) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private static void number(Sheet sheet, int index, String label, long value, CellStyle style) {
        number(sheet, index, label, value);
        var row = sheet.getRow(index);
        if (label.length() > 40) row.setHeightInPoints(52);
        row.getCell(0).setCellStyle(style);
    }

    private static String statusName(TaskStatus status) {
        return switch (status) {
            case NEW -> "Новая";
            case IN_PROGRESS -> "В работе";
            case DONE -> "Выполнена";
            case CANCELLED -> "Отменена";
        };
    }
}
