package ru.vehxi.taskflow.tasks.domain;
import jakarta.persistence.*;
import ru.vehxi.taskflow.common.domain.CreatedEntity;
import ru.vehxi.taskflow.users.domain.User;
import java.time.LocalDate;
/** Persistent timeentry data. */
@Entity @Table(name = "time_entries")
public class TimeEntry extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;
    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;
    @Column(nullable = false, updatable = false)
    private int minutes;
    /** Initializes an entity for persistence. */
    protected TimeEntry() { }
    /**
     * Creates an entity with the supplied data.
     *
     * @param task the task value
     *
     * @param author the author value
     *
     * @param workDate the workDate value
     *
     * @param minutes the minutes value
     */
    public TimeEntry(Task task, User author, LocalDate workDate, int minutes) {
        this.task = task;
        this.author = author;
        this.workDate = workDate;
        this.minutes = minutes;
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
     * Returns the workDate value.
     *
     * @return the workDate value
     */
    public LocalDate getWorkDate() { return workDate; }
    /**
     * Returns the minutes value.
     *
     * @return the minutes value
     */
    public int getMinutes() { return minutes; }
}
