package ru.vehxi.taskflow.tasks.web;

import java.time.LocalDate;
import java.util.List;
import ru.vehxi.taskflow.tasks.domain.Task;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;

/** Public task representations. */
public final class TaskViews {
    private TaskViews() { }

    /** Filter option.
     * @param id option identity
     * @param name option name */
    public record Option(Long id, String name) { }

    /** Available choices derived from authorized tasks.
     * @param projects projects with visible tasks
     * @param assignees assignees in owned projects */
    public record FilterOptions(List<Option> projects, List<Option> assignees) { }

    /** Task list entry with display names needed by the client.
     * @param id task identity
     * @param projectId project identity
     * @param projectName project name
     * @param title task title
     * @param dueDate deadline
     * @param status task status
     * @param assigneeId executor identity
     * @param assigneeName executor name
     * @param version current version */
    public record Summary(Long id, Long projectId, String projectName, String title, LocalDate dueDate,
            TaskStatus status, Long assigneeId, String assigneeName, long version) {
        /** Maps an authorized task.
         * @param task authorized task
         * @return list entry */
        public static Summary from(Task task) {
            return new Summary(task.getId(), task.getProject().getId(), task.getProject().getName(), task.getTitle(),
                    task.getDueDate(), task.getStatus(), task.getAssignee().getId(), task.getAssignee().getName(),
                    task.getVersion());
        }
    }

    /** Task card, including the names of its links.
     * @param id task identity
     * @param projectId project identity
     * @param projectName project name
     * @param managerId owning manager identity
     * @param authorId author's identity
     * @param authorName author's name
     * @param assigneeId executor identity
     * @param assigneeName executor name
     * @param typeId type identity
     * @param typeName type name
     * @param priorityId priority identity
     * @param priorityName priority name
     * @param title task title
     * @param description task description
     * @param dueDate deadline
     * @param status task status
     * @param version current version */
    public record Detail(Long id, Long projectId, String projectName, Long managerId, Long authorId, String authorName,
            Long assigneeId, String assigneeName, Long typeId, String typeName, Long priorityId, String priorityName,
            String title, String description, LocalDate dueDate, TaskStatus status, long version) {
        /** Maps an authorized task.
         * @param task authorized task
         * @return details */
        public static Detail from(Task task) {
            return new Detail(task.getId(), task.getProject().getId(), task.getProject().getName(),
                    task.getProject().getManager().getId(), task.getAuthor().getId(), task.getAuthor().getName(),
                    task.getAssignee().getId(), task.getAssignee().getName(), task.getType().getId(),
                    task.getType().getName(), task.getPriority().getId(), task.getPriority().getName(),
                    task.getTitle(), task.getDescription(), task.getDueDate(), task.getStatus(), task.getVersion());
        }
    }
}
