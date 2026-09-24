package com.example.hospital.api;

import com.example.hospital.api.UserInput;
import com.example.hospital.domain.AuditEvent;
import com.example.hospital.repository.AuditEventRepository;
import com.example.hospital.service.DepartmentTimeService;
import com.example.hospital.service.UserService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
  private final UserService users;
  private final AuditEventRepository audit;
  private final DepartmentTimeService departmentTime;

  public AccountController(
      UserService users, AuditEventRepository audit, DepartmentTimeService departmentTime) {
    this.users = users;
    this.audit = audit;
    this.departmentTime = departmentTime;
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public Object users() {
    return users.list().stream().map(Views::account).toList();
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public Object user(@Valid @RequestBody UserInput in) {
    return Views.account(users.save(null, in));
  }

  @PutMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object user(@PathVariable Long id, @Valid @RequestBody UserInput in) {
    return Views.account(users.save(id, in));
  }

  @GetMapping("/audit")
  @PreAuthorize("hasRole('ADMIN')")
  public Object audit(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Long actorId,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) Long entityId,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);
    if (actorId != null && actorId <= 0)
      throw new ApiException(400, "INVALID_ACTOR_ID", "Actor ID must be a positive integer.");
    if (entityId != null && entityId <= 0)
      throw new ApiException(400, "INVALID_ENTITY_ID", "Entity ID must be a positive integer.");
    if (from != null && to != null && from.isAfter(to))
      throw new ApiException(400, "INVALID_PERIOD", "Start date must be before or equal to end date.");
    var request = PageRequest.of(
        safePage, safeSize, Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id")));
    String normalizedEventType = normalize(eventType);
    String normalizedEntityType = normalize(entityType);
    String normalizedSource = normalize(source);
    var zone = departmentTime.zoneId();
    var fromInstant = from == null ? null : from.atStartOfDay(zone).toInstant();
    var toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
    Specification<AuditEvent> filters = (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      if (normalizedEventType != null)
        predicates.add(cb.equal(cb.lower(cb.trim(root.<String>get("eventType"))), normalizedEventType));
      if (actorId != null) predicates.add(cb.equal(root.get("userId"), actorId));
      if (normalizedEntityType != null)
        predicates.add(cb.equal(cb.lower(cb.trim(root.<String>get("entityType"))), normalizedEntityType));
      if (entityId != null) predicates.add(cb.equal(root.get("entityId"), entityId));
      if (normalizedSource != null)
        predicates.add(cb.equal(cb.lower(cb.trim(root.<String>get("source"))), normalizedSource));
      if (fromInstant != null)
        predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("timestamp"), fromInstant));
      if (toExclusive != null)
        predicates.add(cb.lessThan(root.<Instant>get("timestamp"), toExclusive));
      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
    var all = audit.findAll(filters, request);
    return Map.of(
        "page", safePage,
        "size", safeSize,
        "total", all.getTotalElements(),
        "events", all.getContent().stream().map(Views::audit).toList());
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.strip().toLowerCase(Locale.ROOT);
  }
}
