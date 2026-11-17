package ru.vehxi.taskflow.users.web;

import jakarta.validation.constraints.*;
import java.util.*;
import ru.vehxi.taskflow.users.domain.RoleCode;

/** Editable public fields and an explicit expected version.
     * @param name display name
     * @param email account address
     * @param roles nonempty role selection
 *
     * @param active login availability
     * @param expectedVersion version shown to the caller */
public record UpdateUserRequest(
        @NotBlank(message = "Укажите имя.") @Size(max = 120, message = "Не более 120 символов.") String name,
        @NotBlank(message = "Укажите email.") @Email(message = "Проверьте email.")
        @Size(max = 254, message = "Не более 254 символов.") String email,
        @NotEmpty(message = "Выберите хотя бы одну роль.") Set<@NotNull RoleCode> roles,
        @NotNull(message = "Укажите состояние учётной записи.") Boolean active,
        @NotNull(message = "Передайте версию карточки.") @PositiveOrZero(message = "Версия не может быть отрицательной.") Long expectedVersion) {
    /** Normalizes fields before validation and uniqueness checks. */
    public UpdateUserRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
