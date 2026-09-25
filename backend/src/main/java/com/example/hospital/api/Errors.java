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
    var response = ResponseEntity.status(e.getStatus());
    e.headers().forEach((name, value) -> response.header(name, value));
    return response.body(body(e.getStatus(), e.code, e.getMessage(), r.getRequestURI()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    jakarta.validation.ConstraintViolationException.class,
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
    String detail = detail(e).toLowerCase();
    String code = "DATA_CONFLICT";
    String message =
        "The record conflicts with existing or recently changed data. Refresh and try again.";
    if (detail.contains("department_archived_read_only")) {
      code = "DEPARTMENT_ARCHIVED_READ_ONLY";
      message = "This department is archived and read only. Restore it before making changes.";
    } else if (detail.contains("patient_identifier") || detail.contains("patients_department_id_patient_identifier")) {
      code = "PATIENT_IDENTIFIER_TAKEN";
      message = "A patient with this identifier already exists in the department.";
    } else if (detail.contains("username") || detail.contains("app_users_username")) {
      code = "USERNAME_TAKEN";
      message = "That username is already in use.";
    } else if (detail.contains("join_code") || detail.contains("hospitals_join") || detail.contains("departments_join")) {
      code = "JOIN_CODE_TAKEN";
      message = "That join code is already in use. Rotate and try again.";
    } else if (detail.contains("hospitals_name_unique") || detail.contains("hospitals_name")) {
      code = "HOSPITAL_NAME_TAKEN";
      message = "A hospital with this name already exists.";
    } else if (detail.contains("doctor_identifier")) {
      code = "DOCTOR_IDENTIFIER_TAKEN";
      message = "A doctor with this identifier already exists in the department.";
    } else if (detail.contains("room_number")) {
      code = "ROOM_NUMBER_TAKEN";
      message = "A room with this number already exists in the department.";
    } else if (detail.contains("procedure_code")) {
      code = "PROCEDURE_CODE_TAKEN";
      message = "A procedure with this code already exists in the department.";
    } else if (detail.contains("one_active_admission")) {
      code = "ALREADY_ADMITTED";
      message = "This patient already has an active admission.";
    }
    return ResponseEntity.status(409).body(body(409, code, message, r.getRequestURI()));
  }

  @ExceptionHandler({
    QueryTimeoutException.class,
    org.springframework.transaction.TransactionTimedOutException.class
  })
  ResponseEntity<?> timeout(Exception e, HttpServletRequest r) {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(
            body(
                503,
                "DATABASE_TIMEOUT",
                "The request took too long and was cancelled without saving changes. Try again or narrow the request.",
                r.getRequestURI()));
  }

  @ExceptionHandler({
    DataAccessResourceFailureException.class,
    org.springframework.transaction.CannotCreateTransactionException.class
  })
  ResponseEntity<?> unavailable(Exception e, HttpServletRequest r) {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(
            body(
                503,
                "DATABASE_UNAVAILABLE",
                "The database is busy or unreachable. Try again shortly.",
                r.getRequestURI()));
  }

  private static String detail(Throwable e) {
    var text = new StringBuilder();
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t.getMessage() != null) text.append(t.getMessage()).append(' ');
    }
    return text.toString();
  }
}
