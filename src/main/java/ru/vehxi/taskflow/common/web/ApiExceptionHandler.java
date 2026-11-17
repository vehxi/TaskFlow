package ru.vehxi.taskflow.common.web;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Safe JSON errors; rejected secrets and persistence exception details are never returned. */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    /** Creates the JSON exception mapper. */
    public ApiExceptionHandler() { }

    /** Returns safe operation error.
     * @param failure expected failure
     * @return safe operation error */
    @ExceptionHandler(OperationException.class)
    public ResponseEntity<ErrorResponse> operation(OperationException failure) {
        return response(failure.getStatus(), failure.getCode(), failure.getMessage(), Map.of());
    }
    /** Returns field messages.
     * @param failure invalid body
     * @return field messages */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException failure) {
        Map<String, List<String>> errors = new TreeMap<>();
        failure.getBindingResult().getFieldErrors().forEach(error ->
                errors.computeIfAbsent(field(error.getField()), key -> new ArrayList<>()).add(error.getDefaultMessage()));
        return invalid(errors);
    }
    /** Returns field messages.
     * @param failure invalid service command
     * @return field messages */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraints(ConstraintViolationException failure) {
        Map<String, List<String>> errors = new TreeMap<>();
        failure.getConstraintViolations().forEach(error ->
                errors.computeIfAbsent(field(error.getPropertyPath().toString()), key -> new ArrayList<>()).add(error.getMessage()));
        return invalid(errors);
    }
    /** Returns safe field messages.
     * @param failure invalid route or nested body fields
     * @return safe field messages */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> methodValidation(HandlerMethodValidationException failure) {
        Map<String, List<String>> errors = new TreeMap<>();
        for (var result : failure.getParameterValidationResults()) {
            if (result instanceof org.springframework.validation.method.ParameterErrors fields) {
                fields.getFieldErrors().forEach(error -> errors.computeIfAbsent(field(error.getField()),
                        key -> new ArrayList<>()).add(error.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                errors.put(name == null ? "request" : name, List.of("Проверьте значение поля."));
            }
        }
        return invalid(errors);
    }
    /** Returns safe validation error.
     * @param failure invalid transport or route value
     * @return safe validation error */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> malformed(Exception failure) { return invalid(Map.of()); }

    /** Returns duplicate or rule conflict.
     * @param failure rolled-back integrity failure
     * @return duplicate or rule conflict */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return response(409, "DUPLICATE_VALUE", "Это значение уже используется.", Map.of());
            }
        }
        return response(409, "RULE_CONFLICT", "Изменение нарушает связанные данные.", Map.of());
    }
    /** Returns stale-version error.
     * @param failure concurrent update
     * @return stale-version error */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> stale(OptimisticLockingFailureException failure) {
        return response(409, "STALE_VERSION", "Данные изменились. Обновите карточку и повторите действие.", Map.of());
    }
    /** Returns retry hint.
     * @param failure lock contention
     * @return retry hint */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> locking(PessimisticLockingFailureException failure) {
        return response(409, "RETRY_REQUIRED", "Не удалось завершить изменение. Повторите действие.", Map.of());
    }
    /** Returns sanitized failure.
     * @param failure unexpected exception
     * @return sanitized failure */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception failure) {
        String requestId = UUID.randomUUID().toString();
        LOG.error("Request {} failed ({})", requestId, failure.getClass().getName());
        return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("INTERNAL_ERROR", "Не удалось выполнить запрос.", Map.of(), requestId));
    }
    private static String field(String name) {
        return name.equals("passwordWithinByteLimit") ? "initialPassword" : name;
    }
    private static ResponseEntity<ErrorResponse> invalid(Map<String, List<String>> errors) {
        return response(400, "VALIDATION_ERROR", "Проверьте поля запроса.", errors);
    }
    private static ResponseEntity<ErrorResponse> response(int status, String code, String message,
            Map<String, List<String>> errors) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(code, message, errors, UUID.randomUUID().toString()));
    }
}
