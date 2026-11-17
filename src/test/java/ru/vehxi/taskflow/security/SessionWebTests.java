package ru.vehxi.taskflow.security;

import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import ru.vehxi.taskflow.users.domain.*;
import ru.vehxi.taskflow.users.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionWebTests {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private Accounts accounts;

    @BeforeEach
    void setup() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebConfiguration.class);
        context.refresh();
        accounts = context.getBean(Accounts.class);
        var roleConstructor = Role.class.getDeclaredConstructor();
        roleConstructor.setAccessible(true);
        var role = roleConstructor.newInstance();
        ReflectionTestUtils.setField(role, "code", RoleCode.EXECUTOR);
        accounts.current = new User("Alice", "alice@example.test",
                context.getBean(PasswordEncoder.class).encode("correct-password"), Set.of(role));
        ReflectionTestUtils.setField(accounts.current, "id", 7L);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanup() { if (context != null) context.close(); }

    @Test
    void spaLoginAcceptsEmailAndAccountDtoHidesCredentials() throws Exception {
        var session = login();
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("EXECUTOR"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void anonymousApiReturnsJsonAndCsrfIsAvailable() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void logoutRequiresCsrfAndInvalidatesSession() throws Exception {
        var session = login();
        mvc.perform(post("/api/session/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/session/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void blockedSessionImmediatelyLosesApiAccess() throws Exception {
        var session = login();
        ReflectionTestUtils.setField(accounts.current, "active", false);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void executorCannotAccessUserAdministration() throws Exception {
        mvc.perform(get("/api/users").session(login())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void spaUsesRealCsrfTokensAcrossLoginAndLogout() throws Exception {
        var tokenResponse = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) tokenResponse.getRequest().getSession(false);
        var mapper = new tools.jackson.databind.json.JsonMapper();
        String initialToken = mapper.readTree(tokenResponse.getResponse().getContentAsString()).get("token").asString();
        mvc.perform(post("/api/session").session(session).header("X-CSRF-TOKEN", initialToken)
                .param("email", "alice@example.test").param("password", "correct-password"))
                .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/api/session/logout").session(session).header("X-CSRF-TOKEN", initialToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var refreshed = mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
        String freshToken = mapper.readTree(refreshed.getResponse().getContentAsString()).get("token").asString();
        mvc.perform(post("/api/session/logout").session(session).header("X-CSRF-TOKEN", freshToken))
                .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Location"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void invalidCredentialsAndMissingCsrfNeverRedirectToHtml() throws Exception {
        mvc.perform(post("/api/session").with(csrf()).param("email", "alice@example.test").param("password", "incorrect"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/api/session").param("email", "alice@example.test").param("password", "correct-password"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void executorCannotMutateUsersAndAnonymousCannotReadThem() throws Exception {
        var session = login();
        mvc.perform(post("/api/users").session(session).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(put("/api/users/7").session(session).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void publicSpaRoutesDoNotExposeProtectedApiData() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/admin/users/7")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/api/users/7")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/session").with(csrf()).param("email", " ALICE@EXAMPLE.TEST ")
                        .param("password", "correct-password"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    static class Accounts { User current; }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, AccountController.class, ru.vehxi.taskflow.config.SpaController.class})
    static class WebConfiguration {
        @Bean Accounts accounts() { return new Accounts(); }
        @Bean UserRepository users(Accounts accounts) {
            return (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "findWithRolesByEmail" -> Optional.ofNullable(accounts.current)
                                .filter(u -> u.getEmail().equals(args[0]));
                        case "findWithRolesById" -> Optional.ofNullable(accounts.current)
                                .filter(u -> u.getId().equals(args[0]));
                        case "toString" -> "TestAccountRepository";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
        @Bean DatabaseUserDetailsService userDetailsService(UserRepository users) {
            return new DatabaseUserDetailsService(users);
        }
    }
}
