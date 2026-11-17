package ru.vehxi.taskflow.bootstrap;

import jakarta.validation.Validation;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;
import ru.vehxi.taskflow.users.domain.*;
import ru.vehxi.taskflow.users.repository.*;
import static org.assertj.core.api.Assertions.*;

class BootstrapAdminTests {
    @Test
    void acceptsANonblankShortPasswordAndDoesNotOverwriteIt() throws Exception {
        var saved = new AtomicReference<User>();
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var service = service(saved, encoder, validators.getValidator());
            service.initialize(new BootstrapProperties(true, "Operator", "operator@example.test", "short"));
            var account = saved.get();
            assertThat(encoder.matches("short", account.getPasswordHash())).isTrue();
            service.initialize(new BootstrapProperties(true, "Other", "other@example.test", "different"));
            assertThat(saved.get()).isSameAs(account);
        }
    }

    @Test
    void rejectsBlankOrOversizedUtf8Passwords() throws Exception {
        var saved = new AtomicReference<User>();
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var service = service(saved, encoder, validators.getValidator());
            for (var password : new String[]{"", "   ", "я".repeat(37)}) {
                assertThatThrownBy(() -> service.initialize(
                        new BootstrapProperties(true, "Operator", "operator@example.test", password)))
                        .isInstanceOf(IllegalStateException.class);
            }
            assertThat(saved.get()).isNull();
        }
    }

    private BootstrapAdminService service(AtomicReference<User> saved,
            org.springframework.security.crypto.password.PasswordEncoder encoder,
            jakarta.validation.Validator validator) throws Exception {
        var constructor = Role.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var role = constructor.newInstance();
        ReflectionTestUtils.setField(role, "code", RoleCode.ADMIN);
        UserRepository users = (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "existsByRoles_Code" -> saved.get() != null;
                    case "existsByEmail" -> false;
                    case "saveAndFlush" -> { saved.set((User) args[0]); yield args[0]; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        RoleRepository roles = (RoleRepository) Proxy.newProxyInstance(RoleRepository.class.getClassLoader(),
                new Class<?>[]{RoleRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("findById")) return Optional.of(role);
                    throw new UnsupportedOperationException(method.getName());
                });
        var jdbc = new JdbcTemplate() {
            @Override public void execute(String sql) { /* No database in this validation test. */ }
        };
        return new BootstrapAdminService(users, roles, encoder, jdbc, validator);
    }
}
