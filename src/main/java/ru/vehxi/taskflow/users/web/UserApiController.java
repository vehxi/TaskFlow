package ru.vehxi.taskflow.users.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.vehxi.taskflow.common.web.*;
import ru.vehxi.taskflow.security.TaskFlowPrincipal;
import ru.vehxi.taskflow.users.service.UserAdministrationService;

/** Administrator-only JSON account operations. */
@RestController
@RequestMapping("/api/users")
public class UserApiController {
    private final UserAdministrationService users;
    /** Initializes the component with the supplied arguments.
     * @param users shared transactional operations */
    public UserApiController(UserAdministrationService users) { this.users = users; }

    /** Returns account page.
     * @param actor current account
     * @param page zero-based page
     * @param size page capacity
     * @return account page */
    @GetMapping
    public PageResponse<UserDto> list(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return users.list(actor.getId(), page, size);
    }
    /** Returns public account.
     * @param actor current account
     * @param id target identity
     * @return public account */
    @GetMapping("/{id}")
    public UserDto get(@AuthenticationPrincipal TaskFlowPrincipal actor, @PathVariable @Positive Long id) {
        return users.get(actor.getId(), id);
    }
    /** Returns created account and resource location.
     * @param actor current account
     * @param command initial fields
     * @return created account and resource location */
    @PostMapping
    public ResponseEntity<MutationResponse<UserDto>> create(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @Valid @RequestBody CreateUserRequest command) {
        UserDto result = users.create(actor.getId(), command);
        return ResponseEntity.created(URI.create("/api/users/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(MutationResponse.withoutNotification(result));
    }
    /** Returns saved account.
     * @param actor current account
     * @param id target identity
     * @param command edited fields
     * @return saved account */
    @PutMapping("/{id}")
    public MutationResponse<UserDto> update(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @Valid @RequestBody UpdateUserRequest command) {
        return MutationResponse.withoutNotification(users.update(actor.getId(), id, command));
    }
}
