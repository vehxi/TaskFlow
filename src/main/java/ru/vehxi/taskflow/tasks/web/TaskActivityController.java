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

/** Task transitions and append-only history. */
@RestController
@RequestMapping("/api/tasks/{id}")
public class TaskActivityController {
    private final TaskService tasks;

    /** Creates the task activity endpoints.
     * @param tasks task operations */
    public TaskActivityController(TaskService tasks) { this.tasks = tasks; }

    /** Applies a status transition.
     * @param actor current account
     * @param id task identity
     * @param command requested transition
     * @return updated task */
    @PostMapping("/status")
    public MutationResponse<TaskViews.Detail> status(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @Valid @RequestBody TaskRequests.ChangeStatus command) {
        return MutationResponse.withoutNotification(tasks.changeStatus(actor.getId(), id, command));
    }

    /** Lists comments.
     * @param actor current account
     * @param id task identity
     * @param page zero-based page
     * @param size page capacity
     * @return comment page */
    @GetMapping("/comments")
    public PageResponse<TaskHistoryViews.CommentView> comments(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tasks.comments(actor.getId(), id, page, size);
    }

    /** Returns a comment from the specified task.
     * @param actor current account
     * @param id task identity
     * @param commentId comment identity
     * @return stored comment */
    @GetMapping("/comments/{commentId}")
    public TaskHistoryViews.CommentView comment(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @PathVariable @Positive Long commentId) {
        return tasks.comment(actor.getId(), id, commentId);
    }

    /** Appends a comment.
     * @param actor current account
     * @param id task identity
     * @param command comment details
     * @return saved comment, location and parent version */
    @PostMapping("/comments")
    public ResponseEntity<MutationResponse<TaskHistoryViews.CommentResult>> addComment(
            @AuthenticationPrincipal TaskFlowPrincipal actor, @PathVariable @Positive Long id,
            @Valid @RequestBody TaskRequests.AddComment command) {
        var result = tasks.addComment(actor.getId(), id, command);
        return created(id, "comments", result.comment().id(), result);
    }

    /** Lists work entries with the task total.
     * @param actor current account
     * @param id task identity
     * @param page zero-based page
     * @param size page capacity
     * @return entry page and total */
    @GetMapping("/time-entries")
    public TaskHistoryViews.TimePage timeEntries(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tasks.timeEntries(actor.getId(), id, page, size);
    }

    /** Returns a work entry from the specified task.
     * @param actor current account
     * @param id task identity
     * @param entryId entry identity
     * @return stored work entry */
    @GetMapping("/time-entries/{entryId}")
    public TaskHistoryViews.TimeView timeEntry(@AuthenticationPrincipal TaskFlowPrincipal actor,
            @PathVariable @Positive Long id, @PathVariable @Positive Long entryId) {
        return tasks.timeEntry(actor.getId(), id, entryId);
    }

    /** Appends an executor's work entry.
     * @param actor current account
     * @param id task identity
     * @param command work date and duration
     * @return saved time entry, location and parent version */
    @PostMapping("/time-entries")
    public ResponseEntity<MutationResponse<TaskHistoryViews.TimeResult>> addTime(
            @AuthenticationPrincipal TaskFlowPrincipal actor, @PathVariable @Positive Long id,
            @Valid @RequestBody TaskRequests.AddTime command) {
        var result = tasks.addTime(actor.getId(), id, command);
        return created(id, "time-entries", result.timeEntry().id(), result);
    }

    private static <T> ResponseEntity<MutationResponse<T>> created(Long taskId, String resource, Long id, T data) {
        return ResponseEntity.created(URI.create("/api/tasks/" + taskId + "/" + resource + "/" + id))
                .cacheControl(CacheControl.noStore()).body(MutationResponse.withoutNotification(data));
    }
}
