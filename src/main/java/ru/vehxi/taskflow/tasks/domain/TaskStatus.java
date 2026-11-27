package ru.vehxi.taskflow.tasks.domain;
/** Fixed values used by the database and application. */
public enum TaskStatus {
    /** New. */
    NEW,
    /** In progress. */
    IN_PROGRESS,
    /** Done. */
    DONE,
    /** Cancelled. */
    CANCELLED;

    /** {@return whether the task accepts changes} */
    public boolean isOpen() {
        return this == NEW || this == IN_PROGRESS;
    }
}
