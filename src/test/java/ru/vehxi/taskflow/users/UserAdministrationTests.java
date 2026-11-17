package ru.vehxi.taskflow.users;

import jakarta.validation.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;
import ru.vehxi.taskflow.common.web.OperationException;
import ru.vehxi.taskflow.projects.repository.ProjectRepository;
import ru.vehxi.taskflow.tasks.domain.TaskStatus;
import ru.vehxi.taskflow.tasks.repository.TaskRepository;
import ru.vehxi.taskflow.users.domain.*;
import ru.vehxi.taskflow.users.repository.*;
import ru.vehxi.taskflow.users.service.UserAdministrationService;
import ru.vehxi.taskflow.users.web.*;
import static org.assertj.core.api.Assertions.*;

class UserAdministrationTests {
    private Fixture fixture;
    @BeforeEach void setup() throws Exception { fixture = new Fixture(); }
    @AfterEach void cleanup() { fixture.close(); }

    @Test void createsNormalizedAccountWithAnEncodedPassword() {
        var result = fixture.service.create(9L, new CreateUserRequest("  Анна  ", " ANNA@EXAMPLE.TEST ", "secret", Set.of(RoleCode.EXECUTOR)));
        assertThat(result.name()).isEqualTo("Анна");
        assertThat(result.email()).isEqualTo("anna@example.test");
        assertThat(fixture.encoder.matches("secret", fixture.accounts.get(result.id()).getPasswordHash())).isTrue();
        assertThat(result.roles()).containsExactly(RoleCode.EXECUTOR);
    }
    @Test void rejectsBlankAndOversizedUtf8SecretsAndEmptyRoles() {
        for (String password : new String[]{"", "  ", "я".repeat(37)}) {
            assertThatThrownBy(() -> fixture.service.create(9L,
                    new CreateUserRequest("Anna", "anna@example.test", password, Set.of(RoleCode.EXECUTOR))))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        assertThatThrownBy(() -> fixture.service.create(9L,
                new CreateUserRequest("Anna", "anna@example.test", "secret", Set.of())))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(fixture.accounts).hasSize(2);
    }
    @Test void rejectsDuplicateNormalizedEmail() {
        assertThatThrownBy(() -> fixture.service.create(9L,
                new CreateUserRequest("Other", " TARGET@EXAMPLE.TEST ", "secret", Set.of(RoleCode.EXECUTOR))))
                .isInstanceOfSatisfying(OperationException.class, error -> assertThat(error.getCode()).isEqualTo("DUPLICATE_VALUE"));
    }
    @Test void locksAccountsInAscendingOrderAndPreservesPasswordOnEdit() {
        String hash = fixture.accounts.get(2L).getPasswordHash();
        var result = fixture.service.update(9L, 2L, edit(Set.of(RoleCode.EXECUTOR), false, 0L));
        assertThat(fixture.locks).containsExactly(2L, 9L);
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.active()).isFalse();
        assertThat(fixture.accounts.get(2L).getPasswordHash()).isEqualTo(hash);
    }
    @Test void rejectsStaleVersionBeforeChangingRoles() {
        assertThatThrownBy(() -> fixture.service.update(9L, 2L, edit(Set.of(RoleCode.MANAGER), true, 8L)))
                .isInstanceOfSatisfying(OperationException.class, error -> assertThat(error.getCode()).isEqualTo("STALE_VERSION"));
        assertThat(fixture.writes).isZero();
    }
    @Test void cannotRemoveExecutorFromUnfinishedTasksButCanBlockTheAccount() {
        fixture.unfinishedTasks = true;
        assertThatThrownBy(() -> fixture.service.update(9L, 2L, edit(Set.of(RoleCode.MANAGER), true, 0L)))
                .isInstanceOfSatisfying(OperationException.class, error -> assertThat(error.getCode()).isEqualTo("RULE_CONFLICT"));
        assertThat(fixture.writes).isZero();
        assertThat(fixture.service.update(9L, 2L, edit(Set.of(RoleCode.EXECUTOR), false, 0L)).active()).isFalse();
    }
    @Test void cannotRemoveManagerWithProjects() {
        ReflectionTestUtils.setField(fixture.accounts.get(2L), "roles", new HashSet<>(Set.of(fixture.roles.get(RoleCode.MANAGER))));
        fixture.managedProjects = true;
        assertThatThrownBy(() -> fixture.service.update(9L, 2L, edit(Set.of(RoleCode.EXECUTOR), true, 0L)))
                .isInstanceOfSatisfying(OperationException.class, error -> assertThat(error.getCode()).isEqualTo("RULE_CONFLICT"));
        assertThat(fixture.writes).isZero();
    }
    @Test void rolesOnlyChangeIncrementsVersionAndNoOpDoesNot() {
        var noOp = fixture.service.update(9L, 2L, edit(Set.of(RoleCode.EXECUTOR), true, 0L));
        assertThat(noOp.version()).isZero();
        assertThat(fixture.writes).isZero();
        var result = fixture.service.update(9L, 2L, edit(Set.of(RoleCode.EXECUTOR, RoleCode.MANAGER), true, 0L));
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.roles()).containsExactlyInAnyOrder(RoleCode.MANAGER, RoleCode.EXECUTOR);
        assertThat(fixture.writes).isEqualTo(1);
    }
    @Test void rechecksAdminAndActiveStatusWithinTheOperation() {
        ReflectionTestUtils.setField(fixture.accounts.get(9L), "roles", new HashSet<>(Set.of(fixture.roles.get(RoleCode.EXECUTOR))));
        assertThatThrownBy(() -> fixture.service.update(9L, 2L, edit(Set.of(RoleCode.EXECUTOR), false, 0L)))
                .isInstanceOfSatisfying(OperationException.class, error -> assertThat(error.getStatus()).isEqualTo(403));
        ReflectionTestUtils.setField(fixture.accounts.get(9L), "active", false);
        assertThatThrownBy(() -> fixture.service.list(9L, 0, 20))
                .isInstanceOfSatisfying(OperationException.class, error -> assertThat(error.getStatus()).isEqualTo(401));
        assertThat(fixture.writes).isZero();
    }
    @Test void rejectsUnboundedPagesAndRequiresAnExplicitVersionAndState() {
        assertThatThrownBy(() -> fixture.service.list(9L, 0, 101)).isInstanceOf(OperationException.class);
        assertThatThrownBy(() -> fixture.service.update(9L, 2L,
                new UpdateUserRequest("Target", "target@example.test", Set.of(RoleCode.EXECUTOR), null, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private static UpdateUserRequest edit(Set<RoleCode> roles, boolean active, Long version) {
        return new UpdateUserRequest("Target", "target@example.test", roles, active, version);
    }

    static final class Fixture implements AutoCloseable {
        final Map<Long, User> accounts = new LinkedHashMap<>();
        final Map<RoleCode, Role> roles = new EnumMap<>(RoleCode.class);
        final List<Long> locks = new ArrayList<>();
        final org.springframework.security.crypto.password.PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
        final UserAdministrationService service;
        boolean unfinishedTasks, managedProjects;
        int writes;
        Fixture() throws Exception {
            for (RoleCode code : RoleCode.values()) {
                var constructor = Role.class.getDeclaredConstructor(); constructor.setAccessible(true);
                Role role = constructor.newInstance(); ReflectionTestUtils.setField(role, "code", code); roles.put(code, role);
            }
            account(9L, "Admin", "admin@example.test", RoleCode.ADMIN);
            account(2L, "Target", "target@example.test", RoleCode.EXECUTOR);
            UserRepository users = proxy(UserRepository.class, (method, args) -> switch (method) {
                case "findLockedById" -> { locks.add((Long)args[0]); yield Optional.ofNullable(accounts.get(args[0])); }
                case "findWithRolesById" -> Optional.ofNullable(accounts.get(args[0]));
                case "existsByEmail" -> accounts.values().stream().anyMatch(u -> u.getEmail().equals(args[0]));
                case "saveAndFlush" -> { User user = (User)args[0]; ReflectionTestUtils.setField(user, "id", 10L); accounts.put(10L, user); yield user; }
                case "updateAccount" -> {
                    var user = accounts.get(args[0]);
                    if (user.getVersion() != (Long)args[1]) yield 0;
                    ReflectionTestUtils.setField(user, "name", args[2]); ReflectionTestUtils.setField(user, "email", args[3]);
                    ReflectionTestUtils.setField(user, "active", args[4]); ReflectionTestUtils.setField(user, "version", user.getVersion()+1);
                    writes++; yield 1;
                }
                case "findAll" -> new PageImpl<>(new ArrayList<>(accounts.values()), (Pageable)args[0], accounts.size());
                default -> throw new UnsupportedOperationException(method);
            });
            RoleRepository roleRepository = proxy(RoleRepository.class, (method, args) -> {
                var result = new ArrayList<Role>();
                for (Object code : (Iterable<?>)args[0]) result.add(roles.get(code));
                return result;
            });
            ProjectRepository projects = proxy(ProjectRepository.class, (method, args) -> managedProjects);
            TaskRepository tasks = proxy(TaskRepository.class, (method, args) -> {
                assertThat(new HashSet<>((Collection<?>)args[1])).isEqualTo(Set.of(TaskStatus.NEW, TaskStatus.IN_PROGRESS));
                return unfinishedTasks;
            });
            JdbcTemplate jdbc = new JdbcTemplate() {
                @Override public int update(String sql, Object... args) {
                    User user = accounts.get(args[0]); var updated = new HashSet<>(user.getRoles());
                    Role role = roles.get(RoleCode.valueOf((String)args[1]));
                    if (sql.startsWith("DELETE")) updated.remove(role); else updated.add(role);
                    ReflectionTestUtils.setField(user, "roles", updated); return 1;
                }
            };
            service = new UserAdministrationService(users, roleRepository, projects, tasks, encoder, jdbc, validators.getValidator());
        }
        private void account(long id, String name, String email, RoleCode role) {
            User user = new User(name, email, encoder.encode("old-secret"), Set.of(roles.get(role)));
            ReflectionTestUtils.setField(user, "id", id); accounts.put(id, user);
        }
        @Override public void close() { validators.close(); }
        private interface Call { Object invoke(String method, Object[] args) throws Throwable; }
        private static <T> T proxy(Class<T> type, Call call) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (object, method, args) -> call.invoke(method.getName(), args)));
        }
    }
}
