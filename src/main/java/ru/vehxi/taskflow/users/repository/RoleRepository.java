package ru.vehxi.taskflow.users.repository;
import org.springframework.data.jpa.repository.*;
import ru.vehxi.taskflow.users.domain.*;
/** Persistence access for role objects; HTTP access is checked by services. */
public interface RoleRepository extends JpaRepository<Role, RoleCode> {
}
