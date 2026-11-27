package ru.vehxi.taskflow.references.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.vehxi.taskflow.common.web.MutationResponse;
import ru.vehxi.taskflow.references.ReferenceValue;
import ru.vehxi.taskflow.references.service.ReferenceAdministrationService;
import ru.vehxi.taskflow.security.TaskFlowPrincipal;

/** Administrator-only operations on task types and priorities. */
@RestController
public class ReferenceAdministrationController {
    private final ReferenceAdministrationService references;

    /** @param references transactional reference operations */
    public ReferenceAdministrationController(ReferenceAdministrationService references) {
        this.references = references;
    }

    /** @param id type identity
     * @return the current type */
    @GetMapping("/api/task-types/{id}")
    public ReferenceValue getType(@PathVariable @Positive Long id) {
        return references.getType(id);
    }

    /** @param id priority identity
     * @return the current priority */
    @GetMapping("/api/task-priorities/{id}")
    public ReferenceValue getPriority(@PathVariable @Positive Long id) {
        return references.getPriority(id);
    }

    /** @param actor current account
     * @param command new label
     * @return the created type and its location */
    @PostMapping("/api/task-types")
    public ResponseEntity<MutationResponse<ReferenceValue>> createType(
            @AuthenticationPrincipal TaskFlowPrincipal actor, @Valid @RequestBody Create command) {
        return created("/api/task-types/", references.createType(actor.getId(), command.name()));
    }

    /** @param actor current account
     * @param command new label
     * @return the created priority and its location */
    @PostMapping("/api/task-priorities")
    public ResponseEntity<MutationResponse<ReferenceValue>> createPriority(
            @AuthenticationPrincipal TaskFlowPrincipal actor, @Valid @RequestBody Create command) {
        return created("/api/task-priorities/", references.createPriority(actor.getId(), command.name()));
    }

    /** @param actor current account
     * @param id type identity
     * @param command changes and expected version
     * @return the saved type */
    @PutMapping("/api/task-types/{id}")
    public MutationResponse<ReferenceValue> updateType(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @Valid @RequestBody Update command) {
        return MutationResponse.withoutNotification(references.updateType(
                actor.getId(), id, command.name(), command.active(), command.expectedVersion()));
    }

    /** @param actor current account
     * @param id priority identity
     * @param command changes and expected version
     * @return the saved priority */
    @PutMapping("/api/task-priorities/{id}")
    public MutationResponse<ReferenceValue> updatePriority(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @Valid @RequestBody Update command) {
        return MutationResponse.withoutNotification(references.updatePriority(
                actor.getId(), id, command.name(), command.active(), command.expectedVersion()));
    }

    private static ResponseEntity<MutationResponse<ReferenceValue>> created(String path, ReferenceValue value) {
        return ResponseEntity.created(URI.create(path + value.id())).cacheControl(CacheControl.noStore())
                .body(MutationResponse.withoutNotification(value));
    }

    /** @param name display name for the new active value */
    public record Create(@NotBlank(message = "Введите название.")
                         @Size(max = 80, message = "Название должно содержать не больше 80 символов.") String name) {
        /** Normalizes the display name.
         * @param name submitted name */
        public Create {
            name = name == null ? null : name.strip();
        }
    }

    /** @param name display name after the update
     * @param active whether the value is available for new selections
     * @param expectedVersion version read by the editor */
    public record Update(@NotBlank(message = "Введите название.")
                         @Size(max = 80, message = "Название должно содержать не больше 80 символов.") String name,
                         @NotNull Boolean active, @NotNull @PositiveOrZero Long expectedVersion) {
        /** Normalizes the display name.
         * @param name submitted name
         * @param active submitted availability
         * @param expectedVersion submitted version */
        public Update {
            name = name == null ? null : name.strip();
        }
    }
}
