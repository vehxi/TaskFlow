package ru.vehxi.taskflow.tasks.web;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;

/** Inputs for task commands and searches. */
public final class TaskRequests {
    private TaskRequests() { }

    /** Fields shared by task creation and editing. */
    public interface Details {
        /** {@return title } */
        String title();
        /** {@return description } */
        String description();
        /** {@return assignee identity } */
        Long assigneeId();
        /** {@return deadline in ISO calendar format } */
        String dueDate();
        /** {@return type identity } */
        Long typeId();
        /** {@return priority identity } */
        Long priorityId();
    }

    /** Creates a task in the project specified in the route.
     * @param title task title
     * @param description task description
     * @param assigneeId executor identity
     * @param dueDate deadline in ISO calendar format
     * @param typeId task type
     * @param priorityId task priority */
    public record Create(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @NotNull @Positive Long assigneeId,
            @NotBlank @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String dueDate,
            @NotNull @Positive Long typeId,
            @NotNull @Positive Long priorityId) implements Details {
        /** Normalizes text before validation. */
        public Create {
            title = title == null ? null : title.strip();
            description = description == null ? "" : description.strip();
        }
    }

    /** Edits an open task using the displayed version.
     * @param title task title
     * @param description task description
     * @param assigneeId executor identity
     * @param dueDate deadline in ISO calendar format
     * @param typeId task type
     * @param priorityId task priority
     * @param expectedVersion version supplied by the editor */
    public record Update(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @NotNull @Positive Long assigneeId,
            @NotBlank @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String dueDate,
            @NotNull @Positive Long typeId,
            @NotNull @Positive Long priorityId,
            @NotNull @PositiveOrZero Long expectedVersion) implements Details {
        /** Normalizes text before validation. */
        public Update {
            title = title == null ? null : title.strip();
            description = description == null ? "" : description.strip();
        }
    }

    /** Optional restrictions on the already authorized list.
     * @param projectId project identity
     * @param status current status
     * @param dueFrom inclusive first date
     * @param dueTo inclusive last date
     * @param assigneeId executor identity, manager-only */
    public record Filters(@Positive Long projectId, TaskStatus status,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo, @Positive Long assigneeId) { }
}
