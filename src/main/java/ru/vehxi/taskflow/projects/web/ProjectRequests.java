package ru.vehxi.taskflow.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Commands accepted by the project API. */
public final class ProjectRequests {
    private ProjectRequests() { }

    /** New project details.
     * @param name project name
     * @param description optional text, which may be empty */
    public record Create(
            @NotBlank(message = "Укажите название.") @Size(max = 200, message = "Не более 200 символов.") String name,
            @Size(max = 10000, message = "Не более 10 000 символов.") String description) {
        /** Normalizes details before validation. */
        public Create {
            name = name == null ? null : name.strip();
            description = description == null ? "" : description.strip();
        }
    }

    /** Updated project details.
     * @param name project name
     * @param description optional text, which may be empty
     * @param expectedVersion version supplied by the editor */
    public record Update(
            @NotBlank(message = "Укажите название.") @Size(max = 200, message = "Не более 200 символов.") String name,
            @Size(max = 10000, message = "Не более 10 000 символов.") String description,
            @NotNull @PositiveOrZero Long expectedVersion) {
        /** Normalizes details before validation. */
        public Update {
            name = name == null ? null : name.strip();
            description = description == null ? "" : description.strip();
        }
    }

    /** Adds an executor to the project.
     * @param userId executor identity
     * @param expectedProjectVersion version supplied by the editor */
    public record AddMember(@NotNull @Positive Long userId, @NotNull @PositiveOrZero Long expectedProjectVersion) { }
}
