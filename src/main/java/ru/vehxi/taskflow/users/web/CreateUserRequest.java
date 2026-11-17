package ru.vehxi.taskflow.users.web;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ru.vehxi.taskflow.users.domain.RoleCode;

/** Initial account data normalized before validation.
     * @param name display name
     * @param email account address
     * @param initialPassword initial secret
 *
     * @param roles nonempty role selection */
public record CreateUserRequest(
        @NotBlank(message = "Укажите имя.") @Size(max = 120, message = "Не более 120 символов.") String name,
        @NotBlank(message = "Укажите email.") @Email(message = "Проверьте email.")
        @Size(max = 254, message = "Не более 254 символов.") String email,
        @NotBlank(message = "Укажите пароль.") @Size(max = 72, message = "Пароль должен занимать не более 72 байт UTF-8.") String initialPassword,
        @NotEmpty(message = "Выберите хотя бы одну роль.") Set<@NotNull RoleCode> roles) {
    /** Normalizes public fields without changing the password. */
    public CreateUserRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    /** Returns whether the secret fits the encoder byte limit.
     * @return whether the secret fits the encoder byte limit */
    @JsonIgnore
    @AssertTrue(message = "Пароль должен занимать не более 72 байт UTF-8.")
    public boolean isPasswordWithinByteLimit() {
        return initialPassword == null || initialPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    @Override public String toString() {
        return "CreateUserRequest[name=" + name + ", email=" + email + ", initialPassword=REDACTED, roles=" + roles + "]";
    }
}
