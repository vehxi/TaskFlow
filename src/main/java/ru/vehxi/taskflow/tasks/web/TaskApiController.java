package ru.vehxi.taskflow.tasks.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.vehxi.taskflow.common.web.MutationResponse;
import ru.vehxi.taskflow.common.web.PageResponse;
import ru.vehxi.taskflow.security.TaskFlowPrincipal;
import ru.vehxi.taskflow.tasks.service.TaskService;

/** Task search and assignment API. */
@RestController
@RequestMapping("/api")
public class TaskApiController {
    private final TaskService tasks;

    /** Creates the task controller.
     * @param tasks task operations */
    public TaskApiController(TaskService tasks) {
        this.tasks = tasks;
    }

    /** Returns the authorized task list.
     * @param actor current account
     * @param filters optional search limits
     * @param page zero-based page
     * @param size page capacity
     * @return matching tasks */
    @GetMapping("/tasks")
    public PageResponse<TaskViews.Summary> list(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @Valid @ModelAttribute TaskRequests.Filters filters, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tasks.list(actor.getId(), filters, page, size);
    }

    /** Returns filter options for the current account.
     * @param actor current account
     * @return available choices for task filters */
    @GetMapping("/tasks/filter-options")
    public TaskViews.FilterOptions filterOptions(@AuthenticationPrincipal TaskFlowPrincipal actor) {
        return tasks.filterOptions(actor.getId());
    }

    /** Reads a task card.
     * @param actor current account
     * @param id task key
     * @return visible task */
    @GetMapping("/tasks/{id}")
    public TaskViews.Detail get(@AuthenticationPrincipal TaskFlowPrincipal actor, @PathVariable @Positive Long id) {
        return tasks.get(actor.getId(), id);
    }

    /** Creates an assigned task.
     * @param actor current account
     * @param id project key
     * @param command task fields
     * @return stored task and its location */
    @PostMapping("/projects/{id}/tasks")
    public ResponseEntity<MutationResponse<TaskViews.Detail>> create(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @Valid @RequestBody TaskRequests.Create command) {
        var saved = tasks.create(actor.getId(), id, command);
        return ResponseEntity.created(URI.create("/api/tasks/" + saved.id())).cacheControl(CacheControl.noStore())
                .body(MutationResponse.withoutNotification(saved));
    }

    /** Edits or reassigns a task.
     * @param actor current account
     * @param id task key
     * @param command edited fields and version
     * @return stored task */
    @PutMapping("/tasks/{id}")
    public MutationResponse<TaskViews.Detail> update(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @Valid @RequestBody TaskRequests.Update command) {
        return MutationResponse.withoutNotification(tasks.update(actor.getId(), id, command));
    }
}
