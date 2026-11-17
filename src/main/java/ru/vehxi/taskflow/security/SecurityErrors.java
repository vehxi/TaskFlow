package ru.vehxi.taskflow.security;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
/** Consistent filter-level errors for JSON clients. */
final class SecurityErrors {
    private SecurityErrors() { }
    static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }
    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("""
                {"code":"%s","message":"%s","fieldErrors":{},"requestId":"%s"}
                """.formatted(code, message, UUID.randomUUID()));
    }
}
