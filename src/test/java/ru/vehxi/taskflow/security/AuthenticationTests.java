package ru.vehxi.taskflow.security;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;
import ru.vehxi.taskflow.bootstrap.BootstrapProperties;
import ru.vehxi.taskflow.users.domain.*;
import ru.vehxi.taskflow.users.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;

class AuthenticationTests {
    private final org.springframework.security.crypto.password.PasswordEncoder encoder =
            new SecurityConfiguration().passwordEncoder();
    private User current;
    private final UserRepository repository = (UserRepository) Proxy.newProxyInstance(
            UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class}, (proxy, method, args) -> {
                if (method.getName().equals("findWithRolesByEmail")) {
                    return Optional.ofNullable(current).filter(u -> u.getEmail().equals(args[0]));
                }
                if (method.getName().equals("findWithRolesById")) {
                    return Optional.ofNullable(current).filter(u -> u.getId().equals(args[0]));
                }
                throw new UnsupportedOperationException(method.getName());
            });
    private final DatabaseUserDetailsService users = new DatabaseUserDetailsService(repository);

    @BeforeEach
    void account() throws Exception {
        current = new User("Alice", "alice@example.test", encoder.encode("correct-password"), Set.of(role(RoleCode.MANAGER)));
        ReflectionTestUtils.setField(current, "id", 7L);
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void authenticatesNormalizedEmailAndErasesThePassword() {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        var manager = new ProviderManager(provider);
        var result = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                "  ALICE@EXAMPLE.TEST ", "correct-password"));
        assertThat(result.isAuthenticated()).isTrue();
        var principal = (TaskFlowPrincipal) result.getPrincipal();
        assertThat(principal.getId()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("alice@example.test");
        assertThat(principal.getPassword()).isNull();
        assertThat(result.getCredentials()).isNull();
    }

    @Test
    void rejectsOverlongPasswordWithoutAnInternalError() {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice@example.test", "я".repeat(37))))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsWrongPassword() {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice@example.test", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsBlockedAccountAtLogin() {
        ReflectionTestUtils.setField(current, "active", false);
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice@example.test", "correct-password")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void refreshesRolesInAnExistingSession() throws Exception {
        var principal = users.loadCurrentUser(7L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        ReflectionTestUtils.setField(current, "roles", Set.of(role(RoleCode.EXECUTOR)));
        var request = new MockHttpServletRequest("GET", "/api/me");
        request.setSession(new MockHttpSession());
        var reached = new AtomicBoolean();
        new CurrentUserFilter(users).doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_EXECUTOR");
        assertThat(((TaskFlowPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getPassword()).isNull();
    }

    @Test
    void invalidatesAnExistingSessionAfterBlocking() throws Exception {
        var principal = users.loadCurrentUser(7L);
        var context = SecurityContextHolder.getContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        var request = new MockHttpServletRequest("GET", "/api/me");
        request.setSession(session);
        var response = new MockHttpServletResponse();
        ReflectionTestUtils.setField(current, "active", false);
        new CurrentUserFilter(users).doFilter(request, response, (req, res) -> fail("Blocked request reached controller"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTHENTICATION_REQUIRED");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void bootstrapConfigurationDoesNotPrintCredentials() {
        var config = new BootstrapProperties(true, "Admin", "admin@example.test", "do-not-print-this");
        assertThat(config.toString()).doesNotContain("do-not-print-this", "admin@example.test");
    }

    private Role role(RoleCode code) throws Exception {
        var constructor = Role.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var role = constructor.newInstance();
        ReflectionTestUtils.setField(role, "code", code);
        return role;
    }
}
