package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.domain.AuditEvent;
import com.example.hospital.repository.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** Builds the shared, department-scoped audit history query used by pages and exports. */
@Service
public class AuditEventQueryService {
  public static final int MAX_EXPORT_ROWS = 1000;

  public record Filters(
      String eventType,
      Long actorId,
      String entityType,
      Long entityId,
      String source,
      LocalDate from,
      LocalDate to) {}

  private final AuditEventRepository events;
  private final DepartmentTimeService departmentTime;

  public AuditEventQueryService(AuditEventRepository events, DepartmentTimeService departmentTime) {
    this.events = events;
    this.departmentTime = departmentTime;
  }

  public Page<AuditEvent> page(Filters filters, Pageable pageable) {
    return events.findAll(specification(filters), pageable);
  }

  public List<AuditEvent> export(Filters filters, int limit) {
    if (limit < 1 || limit > MAX_EXPORT_ROWS)
      throw new ApiException(
          400, "INVALID_EXPORT_LIMIT", "Export limit must be between 1 and " + MAX_EXPORT_ROWS + ".");
    var request = PageRequest.of(
        0, limit + 1, Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id")));
    var page = events.findAll(specification(filters), request);
    if (page.getContent().size() > limit)
      throw new ApiException(
          400,
          "EXPORT_LIMIT_EXCEEDED",
          "More audit events match than the requested export limit; narrow the filters.");
    return page.getContent();
  }

  private Specification<AuditEvent> specification(Filters filters) {
    if (filters.actorId() != null && filters.actorId() <= 0)
      throw new ApiException(400, "INVALID_ACTOR_ID", "Actor ID must be a positive integer.");
    if (filters.entityId() != null && filters.entityId() <= 0)
      throw new ApiException(400, "INVALID_ENTITY_ID", "Entity ID must be a positive integer.");
    if (filters.from() != null && filters.to() != null && filters.from().isAfter(filters.to()))
      throw new ApiException(400, "INVALID_PERIOD", "Start date must be before or equal to end date.");

    String eventType = normalize(filters.eventType());
    String entityType = normalize(filters.entityType());
    String source = normalize(filters.source());
    var zone = departmentTime.zoneId();
    Instant from = filters.from() == null ? null : filters.from().atStartOfDay(zone).toInstant();
    Instant toExclusive = filters.to() == null
        ? null : filters.to().plusDays(1).atStartOfDay(zone).toInstant();
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (eventType != null)
        predicates.add(cb.equal(cb.lower(cb.trim(root.<String>get("eventType"))), eventType));
      if (filters.actorId() != null) predicates.add(cb.equal(root.get("userId"), filters.actorId()));
      if (entityType != null)
        predicates.add(cb.equal(cb.lower(cb.trim(root.<String>get("entityType"))), entityType));
      if (filters.entityId() != null)
        predicates.add(cb.equal(root.get("entityId"), filters.entityId()));
      if (source != null)
        predicates.add(cb.equal(cb.lower(cb.trim(root.<String>get("source"))), source));
      if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("timestamp"), from));
      if (toExclusive != null)
        predicates.add(cb.lessThan(root.<Instant>get("timestamp"), toExclusive));
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.strip().toLowerCase(Locale.ROOT);
  }
}
