package ru.vehxi.taskflow.security;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
/** Session authentication, role restrictions and CSRF protection. */
@Configuration
public class SecurityConfiguration {
    /** Creates security configuration. */
    public SecurityConfiguration() { }
    /**
     * Returns the password encoder for stored, versioned hashes.
     *
     * @return the password encoder for stored, versioned hashes
     */
    @Bean public PasswordEncoder passwordEncoder() {
        return new TaskFlowPasswordEncoder();
    }
    /**
     * Builds the common security chain for HTML and JSON routes.
     *
     * @param http HTTP security builder
     *
     * @param users database authentication service
     *
     * @return the configured chain
     *
     * @throws Exception if configuration fails
     */
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http, DatabaseUserDetailsService users)
            throws Exception {
        http.authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.FORWARD, jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/", "/login", "/admin/users", "/admin/users/new", "/admin/users/*", "/admin/reference-data", "/account",
                        "/projects", "/projects/*", "/projects/*/tasks/new", "/projects/*/report", "/tasks", "/tasks/*", "/tasks/*/edit",
                        "/index.html", "/assets/**", "/favicon.svg", "/api/csrf").permitAll()
                .requestMatchers("/api/session", "/api/session/logout", "/error").permitAll()
                .requestMatchers("/api/users", "/api/users/**",
                        "/api/task-types", "/api/task-types/*", "/api/task-priorities", "/api/task-priorities/*").hasRole("ADMIN")
                .requestMatchers("/api/assignable-users", "/api/projects/*/members", "/api/projects/*/members/*")
                    .hasRole("MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/projects", "/api/projects/*/tasks").hasRole("MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/projects/*", "/api/tasks/*").hasRole("MANAGER")
                .requestMatchers("/api/projects/*/report", "/api/projects/*/report.xlsx",
                        "/api/projects/*/report/overdue").hasRole("MANAGER")
                .requestMatchers("/api/projects", "/api/projects/**", "/api/tasks", "/api/tasks/**")
                    .hasAnyRole("MANAGER", "EXECUTOR")
                .requestMatchers("/api/me", "/api/reference-data").authenticated()
                .anyRequest().denyAll())
            .formLogin(f -> f.loginPage("/login").loginProcessingUrl("/api/session").usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    response.setStatus(204); response.setHeader("Cache-Control", "no-store");
                })
                .failureHandler((request, response, exception) ->
                    SecurityErrors.write(response, 401, "INVALID_CREDENTIALS", "Проверьте email и пароль.")))
            .logout(l -> l.logoutUrl("/api/session/logout").logoutSuccessHandler((request, response, authentication) -> {
                response.setStatus(204); response.setHeader("Cache-Control", "no-store");
            }))
            .requestCache(org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, exception) ->
                    SecurityErrors.write(response, 401, "AUTHENTICATION_REQUIRED", "Войдите в систему."))
                .accessDeniedHandler((request, response, exception) ->
                    SecurityErrors.write(response, 403,
                            exception instanceof CsrfException ? "CSRF_INVALID" : "ACCESS_DENIED",
                            "Действие недоступно.")))
            .addFilterBefore(new CurrentUserFilter(users), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
