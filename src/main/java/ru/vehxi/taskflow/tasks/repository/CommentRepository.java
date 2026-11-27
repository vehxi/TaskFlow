package ru.vehxi.taskflow.tasks.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import ru.vehxi.taskflow.tasks.domain.Comment;
/** Persistence access for comment objects; HTTP access is checked by services. */
public interface CommentRepository extends JpaRepository<Comment, Long> {
    /** Lists the comments and their authors for a task.
     * @param taskId parent task key
     * @param pageable stable ordering and page size
     * @return comment page */
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByTask_Id(Long taskId, Pageable pageable);

    /** Reads a comment within the authorized task.
     * @param id comment key
     * @param taskId parent task key
     * @return matching comment */
    Optional<Comment> findByIdAndTask_Id(Long id, Long taskId);
}
