package ru.vehxi.taskflow.projects.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.vehxi.taskflow.common.web.MutationResponse;
import ru.vehxi.taskflow.common.web.PageResponse;
import ru.vehxi.taskflow.projects.service.ProjectService;
import ru.vehxi.taskflow.security.TaskFlowPrincipal;

/** JSON endpoints for projects and project participants. */
@RestController
@RequestMapping("/api")
public class ProjectApiController {
    private final ProjectService projects;

    /** Creates the project controller.
     * @param projects transactional operations */
    public ProjectApiController(ProjectService projects) {
        this.projects = projects;
    }

    /** Returns the visible projects.
     * @param actor current account
     * @param page zero-based page
     * @param size page capacity
     * @return visible projects */
    @GetMapping("/projects")
    public PageResponse<ProjectSummaryDto> list(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return projects.list(actor.getId(), page, size);
    }

    /** Returns visible project details.
     * @param actor current account
     * @param id project identity
     * @return project summary */
    @GetMapping("/projects/{id}")
    public ProjectSummaryDto get(@AuthenticationPrincipal TaskFlowPrincipal actor, @PathVariable @Positive Long id) {
        return projects.get(actor.getId(), id);
    }

    /** Creates a project.
     * @param actor current account
     * @param command new project details
     * @return created project and location */
    @PostMapping("/projects")
    public ResponseEntity<MutationResponse<ProjectSummaryDto>> create(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @Valid @RequestBody ProjectRequests.Create command) {
        var project = projects.create(actor.getId(), command);
        return ResponseEntity.created(URI.create("/api/projects/" + project.id()))
                .cacheControl(CacheControl.noStore()).body(MutationResponse.withoutNotification(project));
    }

    /** Updates a project.
     * @param actor current account
     * @param id project identity
     * @param command updated details and version
     * @return saved project */
    @PutMapping("/projects/{id}")
    public MutationResponse<ProjectSummaryDto> update(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @Valid @RequestBody ProjectRequests.Update command) {
        return MutationResponse.withoutNotification(projects.update(actor.getId(), id, command));
    }

    /** Returns project membership.
     * @param actor current account
     * @param id project identity
     * @param page zero-based page
     * @param size page capacity
     * @param assignableOnly whether to limit the list to active executors
     * @return members and project version */
    @GetMapping("/projects/{id}/members")
    public ProjectMembersPage members(@AuthenticationPrincipal TaskFlowPrincipal actor, @PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean assignableOnly) {
        return projects.members(actor.getId(), id, page, size, assignableOnly);
    }

    /** Returns active executors eligible for selection.
     * @param actor current account
     * @param projectId optional owned project for excluding existing members
     * @param page zero-based page
     * @param size page capacity
     * @return minimal executor details */
    @GetMapping("/assignable-users")
    public PageResponse<ProjectMemberDto> candidates(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @RequestParam(required = false) @Positive Long projectId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return projects.candidates(actor.getId(), projectId, page, size);
    }

    /** Adds a project member.
     * @param actor current account
     * @param id project identity
     * @param command participant and expected version
     * @return new project version */
    @PostMapping("/projects/{id}/members")
    public MutationResponse<MembershipChange> add(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @Valid @RequestBody ProjectRequests.AddMember command) {
        return MutationResponse.withoutNotification(new MembershipChange(projects.addMember(actor.getId(), id, command)));
    }

    /** Removes a project member.
     * @param actor current account
     * @param id project identity
     * @param userId participant identity
     * @param expectedProjectVersion version supplied by the editor
     * @return new project version */
    @DeleteMapping("/projects/{id}/members/{userId}")
    public MutationResponse<MembershipChange> remove(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @PathVariable @Positive Long userId,
            @RequestParam @PositiveOrZero long expectedProjectVersion) {
        return MutationResponse.withoutNotification(
                new MembershipChange(projects.removeMember(actor.getId(), id, userId, expectedProjectVersion)));
    }

    /** Result of saving a change to the composition.
     * @param projectVersion new version */
    public record MembershipChange(long projectVersion) { }
}
