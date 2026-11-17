package ru.vehxi.taskflow.security;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import ru.vehxi.taskflow.users.domain.RoleCode;
/** Exposes session information without persistence entities or password hashes. */
@RestController
public class AccountController {
    /** Creates the account endpoints. */
    public AccountController() { }
    /**
     * Returns the current account DTO.
     *
     * @param principal authenticated account
     * @return the current account DTO
     */
    @GetMapping("/api/me") public AccountDto me(@AuthenticationPrincipal TaskFlowPrincipal principal) {
        return new AccountDto(principal.getId(), principal.getDisplayName(), principal.getUsername(), principal.getRoleCodes());
    }
    /**
     * Returns a token for form or JSON commands.
     *
     * @param token current CSRF token
     * @return a token for form or JSON commands
     */
    @GetMapping("/api/csrf") public ResponseEntity<CsrfDto> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfDto(token.getToken(), token.getHeaderName()));
    }
    /**
     * Public account data.
     *
     * @param id account identity
     *
     * @param name display name
     *
     * @param email normalized email
     *
     * @param roles current roles
     */
    public record AccountDto(Long id, String name, String email, Set<RoleCode> roles) { }
    /**
     * CSRF data for a session client.
     *
     * @param token the token value
     *
     * @param headerName the request header name
     */
    public record CsrfDto(String token, String headerName) { }
}
