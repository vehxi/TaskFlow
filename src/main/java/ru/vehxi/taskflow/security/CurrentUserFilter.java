package ru.vehxi.taskflow.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
/** Refreshes roles and rejects blocked accounts in already open sessions. */
final class CurrentUserFilter extends OncePerRequestFilter {
    private final DatabaseUserDetailsService users;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    CurrentUserFilter(DatabaseUserDetailsService users) { this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var context = SecurityContextHolder.getContext();
        var authentication = context.getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof TaskFlowPrincipal previous) {
            try {
                var current = users.loadCurrentUser(previous.getId());
                if (!current.isEnabled()) { throw new UsernameNotFoundException("Account unavailable"); }
                current.eraseCredentials();
                var fresh = UsernamePasswordAuthenticationToken.authenticated(current, null, current.getAuthorities());
                fresh.setDetails(authentication.getDetails());
                context.setAuthentication(fresh);
                contexts.saveContext(context, request, response);
            } catch (UsernameNotFoundException unavailable) {
                var session = request.getSession(false);
                SecurityContextHolder.clearContext();
                if (session != null) { session.invalidate(); }
                if (SecurityErrors.isApi(request)) {
                    SecurityErrors.write(response, 401, "AUTHENTICATION_REQUIRED", "Войдите в систему.");
                    return;
                }
                // Public SPA assets remain available after session invalidation.
                // React discovers the anonymous state through /api/me.
            }
        }
        chain.doFilter(request, response);
    }
}
