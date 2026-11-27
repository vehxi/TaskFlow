package ru.vehxi.taskflow.tasks.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import ru.vehxi.taskflow.tasks.domain.Comment;
import ru.vehxi.taskflow.tasks.domain.TimeEntry;

/** Immutable comments and time entries exposed on the task card. */
public final class TaskHistoryViews {
    private TaskHistoryViews() { }

    /** Comment with the original author's identity.
     * @param id comment identity
     * @param authorId original author
     * @param authorName author display name
     * @param body saved text
     * @param createdAt creation time */
    public record CommentView(Long id, Long authorId, String authorName, String body, Instant createdAt) {
        /** Maps a stored comment.
         * @param comment stored comment
         * @return public view */
        public static CommentView from(Comment comment) {
            return new CommentView(comment.getId(), comment.getAuthor().getId(), comment.getAuthor().getName(),
                    comment.getBody(), comment.getCreatedAt());
        }
    }

    /** Work logged by an executor.
     * @param id entry identity
     * @param authorId original executor
     * @param authorName author display name
     * @param workDate calendar day of work
     * @param minutes duration in whole minutes
     * @param createdAt creation time */
    public record TimeView(Long id, Long authorId, String authorName, LocalDate workDate, int minutes, Instant createdAt) {
        /** Maps a stored time entry.
         * @param entry stored time entry
         * @return public view */
        public static TimeView from(TimeEntry entry) {
            return new TimeView(entry.getId(), entry.getAuthor().getId(), entry.getAuthor().getName(),
                    entry.getWorkDate(), entry.getMinutes(), entry.getCreatedAt());
        }
    }

    /** Time entries and their sum from one snapshot.
     * @param items entries on the requested page
     * @param page zero-based page
     * @param size page capacity
     * @param totalElements entry count
     * @param totalPages page count
     * @param totalMinutes sum across the whole task
     * @param today current calendar date in the application zone */
    public record TimePage(List<TimeView> items, int page, int size, long totalElements, int totalPages,
            long totalMinutes, LocalDate today) {
        /** Maps a time page with its summary.
         * @param source requested records
         * @param totalMinutes task duration
         * @param today date used to validate new entries
         * @return time page */
        public static TimePage from(Page<TimeView> source, long totalMinutes, LocalDate today) {
            return new TimePage(source.getContent(), source.getNumber(), source.getSize(),
                    source.getTotalElements(), source.getTotalPages(), totalMinutes, today);
        }
    }

    /** Saved comment and the new parent version.
     * @param comment new comment
     * @param taskVersion new task version */
    public record CommentResult(CommentView comment, long taskVersion) { }

    /** Saved work entry and the new parent version.
     * @param timeEntry new entry
     * @param taskVersion new task version */
    public record TimeResult(TimeView timeEntry, long taskVersion) { }
}
