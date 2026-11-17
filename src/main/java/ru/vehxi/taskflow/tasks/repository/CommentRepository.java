package ru.vehxi.taskflow.tasks.repository;
import org.springframework.data.jpa.repository.*;
import ru.vehxi.taskflow.tasks.domain.*;
/** Persistence access for comment objects; HTTP access is checked by services. */
public interface CommentRepository extends JpaRepository<Comment, Long> {
}
