package ru.vehxi.taskflow.tasks.domain;
import jakarta.persistence.*;
import ru.vehxi.taskflow.common.domain.CreatedEntity;
import ru.vehxi.taskflow.users.domain.User;
/** Persistent comment data. */
@Entity @Table(name = "comments")
public class Comment extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String body;
    /** Initializes an entity for persistence. */
    protected Comment() { }
    /**
     * Creates an entity with the supplied data.
     *
     * @param task the task value
     *
     * @param author the author value
     *
     * @param body the body value
     */
    public Comment(Task task, User author, String body) {
        this.task = task;
        this.author = author;
        this.body = body;
    }
    /**
     * Returns the task value.
     *
     * @return the task value
     */
    public Task getTask() { return task; }
    /**
     * Returns the author value.
     *
     * @return the author value
     */
    public User getAuthor() { return author; }
    /**
     * Returns the body value.
     *
     * @return the body value
     */
    public String getBody() { return body; }
}
