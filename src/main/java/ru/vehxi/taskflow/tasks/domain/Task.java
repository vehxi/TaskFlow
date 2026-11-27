package ru.vehxi.taskflow.tasks.domain;
import jakarta.persistence.*;
import ru.vehxi.taskflow.common.domain.VersionedCreatedEntity;
import ru.vehxi.taskflow.projects.domain.Project;
import ru.vehxi.taskflow.users.domain.User;
import ru.vehxi.taskflow.references.domain.TaskType;
import ru.vehxi.taskflow.references.domain.TaskPriority;
import java.time.LocalDate;
/** Persistent task data. */
@Entity @Table(name = "tasks")
public class Task extends VersionedCreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assignee_id", nullable = false)
    private User assignee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "type_id", nullable = false)
    private TaskType type;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "priority_id", nullable = false)
    private TaskPriority priority;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private TaskStatus status;
    /** Initializes an entity for persistence. */
    protected Task() { }
    /**
     * Creates an entity with the supplied data.
     *
     * @param project the project value
     *
     * @param author the author value
     *
     * @param assignee the assignee value
     *
     * @param type the type value
     *
     * @param priority the priority value
     *
     * @param title the title value
     *
     * @param description the description value
     *
     * @param dueDate the dueDate value
     *
     * @param status the status value
     */
    public Task(Project project, User author, User assignee, TaskType type, TaskPriority priority, String title, String description, LocalDate dueDate, TaskStatus status) {
        this.project = project;
        this.author = author;
        this.assignee = assignee;
        this.type = type;
        this.priority = priority;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
    }
    /**
     * Returns the project value.
     *
     * @return the project value
     */
    public Project getProject() { return project; }
    /**
     * Returns the author value.
     *
     * @return the author value
     */
    public User getAuthor() { return author; }
    /**
     * Returns the assignee value.
     *
     * @return the assignee value
     */
    public User getAssignee() { return assignee; }
    /**
     * Returns the type value.
     *
     * @return the type value
     */
    public TaskType getType() { return type; }
    /**
     * Returns the priority value.
     *
     * @return the priority value
     */
    public TaskPriority getPriority() { return priority; }
    /**
     * Returns the title value.
     *
     * @return the title value
     */
    public String getTitle() { return title; }
    /**
     * Returns the description value.
     *
     * @return the description value
     */
    public String getDescription() { return description; }
    /**
     * Returns the dueDate value.
     *
     * @return the dueDate value
     */
    public LocalDate getDueDate() { return dueDate; }
    /**
     * Returns the status value.
     *
     * @return the status value
     */
    public TaskStatus getStatus() { return status; }

    /** Changes the editable fields while keeping the project, author and status.
     * @param title normalized title
     * @param description normalized description
     * @param assignee current executor
     * @param dueDate calendar deadline
     * @param type task type
     * @param priority task priority */
    public void updateDetails(String title, String description, User assignee, LocalDate dueDate,
            TaskType type, TaskPriority priority) {
        this.title = title;
        this.description = description;
        this.assignee = assignee;
        this.dueDate = dueDate;
        this.type = type;
        this.priority = priority;
    }
}
