package ru.vehxi.taskflow.users;

import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import ru.vehxi.taskflow.common.web.ApiExceptionHandler;
import ru.vehxi.taskflow.security.TaskFlowPrincipal;
import ru.vehxi.taskflow.users.domain.RoleCode;
import ru.vehxi.taskflow.users.web.UserApiController;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserApiTests {
    private UserAdministrationTests.Fixture fixture;
    private MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        fixture = new UserAdministrationTests.Fixture();
        var principal = new TaskFlowPrincipal(fixture.accounts.get(9L));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        mvc = MockMvcBuilders.standaloneSetup(new UserApiController(fixture.service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build())).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); fixture.close(); }

    @Test void returnsCreatedLocationAndSafeMutationEnvelope() throws Exception {
        String body = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Anna","email":"ANNA@example.test","initialPassword":"new-secret","roles":["EXECUTOR"]}
                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/users/10"))
                .andExpect(jsonPath("$.data.email").value("anna@example.test"))
                .andExpect(jsonPath("$.notificationStatus").value("NOT_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password", "new-secret", "old-secret", "bcrypt");
    }
    @Test void rejectsUnknownFieldsFractionalVersionsAndAbsentVersions() throws Exception {
        for (String extra : new String[]{"\"expectedVersion\":0,\"passwordHash\":\"intruder\"", "\"expectedVersion\":0.5", "\"createdAt\":\"today\""}) {
            mvc.perform(put("/api/users/2").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"name\":\"Target\",\"email\":\"target@example.test\",\"roles\":[\"EXECUTOR\"],\"active\":true," + extra + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(put("/api/users/2").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Target","email":"target@example.test","roles":["EXECUTOR"],"active":true}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.expectedVersion").isArray());
        assertThat(fixture.writes).isZero();
    }
    @Test void validationDoesNotEchoSecretsAndUsesPasswordField() throws Exception {
        String secret = "я".repeat(37);
        String body = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\":\"Anna\",\"email\":\"anna@example.test\",\"initialPassword\":\""+secret+"\",\"roles\":[\"EXECUTOR\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.initialPassword").isArray())
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(secret);
    }
    @Test void returnsPaginationAndRejectsInvalidPageOrId() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/users?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/800")).andExpect(status().isNotFound());
    }
    @Test void returnsConflictForStaleCard() throws Exception {
        mvc.perform(put("/api/users/2").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Changed","email":"target@example.test","roles":["EXECUTOR"],"active":true,"expectedVersion":5}
                """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(fixture.writes).isZero();
    }
}
