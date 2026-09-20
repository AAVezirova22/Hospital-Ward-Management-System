package com.example.hospital.api;

import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  private final com.example.hospital.service.AuditService audit;

  public Errors(com.example.hospital.service.AuditService audit) {
    this.audit = audit;
  }

  public static Map<String, Object> body(int status, String code, String message, String path) {
    return Map.of(
        "timestamp",
        Instant.now().toString(),
        "status",
        status,
        "code",
        code,
        "message",
        message,
        "path",
        path);
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e, HttpServletRequest r) {
    return ResponseEntity.status(e.status)
        .body(body(e.status, e.code, e.getMessage(), r.getRequestURI()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalid(Exception e, HttpServletRequest r) {
    return ResponseEntity.badRequest()
        .body(
            body(
                400,
                "VALIDATION_ERROR",
                "Check the supplied fields and values.",
                r.getRequestURI()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> denied(Exception e, HttpServletRequest r) {
    audit.log(
        "ACCESS_DENIED", "Request", null, r.getRequestURI().contains("assistant") ? "AI" : "UI");
    return ResponseEntity.status(403)
        .body(
            body(
                403,
                "ACCESS_DENIED",
                "You do not have access to this operation.",
                r.getRequestURI()));
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class,
    CannotAcquireLockException.class
  })
  ResponseEntity<?> conflict(Exception e, HttpServletRequest r) {
    return ResponseEntity.status(409)
        .body(
            body(
                409,
                "DATA_CONFLICT",
                "The record conflicts with existing or recently changed data. Refresh and try"
                    + " again.",
                r.getRequestURI()));
  }
}
