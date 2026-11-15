package com.capstone.fraudcase.api;

import com.capstone.fraudcase.application.CaseNotFoundException;
import com.capstone.fraudcase.application.ValidationFailedException;
import com.capstone.fraudcase.domain.CaseStateMachine;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe API error responses: field errors where useful, never stack traces or provider details. */
@RestControllerAdvice(basePackageClasses = CaseApiController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }

    @ExceptionHandler(ValidationFailedException.class)
    ResponseEntity<Map<String, Object>> invalid(ValidationFailedException e) {
        return body(HttpStatus.BAD_REQUEST, "Validation failed", e.fieldErrors());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request, unknown field or unknown enum value", Map.of());
    }

    @ExceptionHandler(CaseNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(CaseNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "Not found", Map.of());
    }

    @ExceptionHandler({CaseStateMachine.InvalidStateTransitionException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Map<String, Object>> conflict(RuntimeException e) {
        return body(HttpStatus.CONFLICT, e instanceof CaseStateMachine.InvalidStateTransitionException ? e.getMessage()
                : "Case was modified concurrently; reload and retry", Map.of());
    }

    /** Unique dedupe_key violation: a concurrent identical intake won the race; the existing case is the answer. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> duplicate(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "An identical case already exists (duplicate intake)", Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badArgument(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, "Invalid argument", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("Unhandled API error type={}", e.getClass().getSimpleName());
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", Map.of());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        body.put("fieldErrors", fields);
        body.put("correlationId", MDC.get("correlationId"));
        return ResponseEntity.status(status).body(body);
    }
}
