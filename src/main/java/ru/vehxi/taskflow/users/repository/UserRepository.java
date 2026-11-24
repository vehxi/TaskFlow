package ru.vehxi.taskflow.users.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.repository.query.Param;
import ru.vehxi.taskflow.users.domain.*;
/** Persistence access for user objects; HTTP access is checked by services. */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Returns the matching account with roles.
     *
     * @param email normalized email
     * @return the matching account with roles
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByEmail(String email);
    /**
     * Returns the account with its current roles.
     *
     * @param id account key
     * @return the account with its current roles
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);
    /**
     * Returns whether any account has the role.
     *
     * @param code role to check
     * @return whether any account has the role
     */
    boolean existsByRoles_Code(RoleCode code);
    /**
     * Returns whether the address is registered.
     *
     * @param email normalized email
     * @return whether the address is registered
     */
    boolean existsByEmail(String email);

    /** Returns eligible accounts for the selected role.
     * @param code required role
     * @param projectId project to exclude existing members from, or zero for all candidates
     * @param pageable stable pagination
     * @return active accounts */
    @Query("""
            select u from User u join u.roles r where u.active = true and r.code = :code
                and (:projectId = 0 or not exists (
                    select m.id.userId from ProjectMembership m
                    where m.id.projectId = :projectId and m.id.userId = u.id))
            """)
    Page<User> findAssignable(@Param("code") RoleCode code, @Param("projectId") long projectId, Pageable pageable);
    /** Returns locked account; roles are loaded after locking the user row.
     * @param id account key
     * @return locked account; roles are loaded after locking the user row */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select u from User u where u.id = :id")
    Optional<User> findLockedById(@Param("id") Long id);

    /** Returns updated row count.
     * @param id target key
     * @param version expected version
     * @param name normalized name
     *
     * @param email normalized email
     * @param active login availability
     * @return updated row count */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.name = :name, u.email = :email, u.active = :active, u.version = u.version + 1 where u.id = :id and u.version = :version")
    int updateAccount(@Param("id") Long id, @Param("version") long version,
            @Param("name") String name, @Param("email") String email, @Param("active") boolean active);
}
