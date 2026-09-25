package com.example.hospital.api;

import com.example.hospital.domain.AuditEvent;
import com.example.hospital.security.DepartmentContext;
import com.example.hospital.service.AuditEventQueryService;
import com.example.hospital.service.AuditService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditExportController {
  /** Fields a redacted export leaves out or replaces; recorded with every redacted export. */
  static final List<String> REDACTED_FIELDS = List.of("actorId", "entityId", "metadata");

  private final AuditEventQueryService events;
  private final AuditService audit;

  public AuditExportController(AuditEventQueryService events, AuditService audit) {
    this.events = events;
    this.audit = audit;
  }

  @GetMapping(value = "/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<String> csv(
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Long actorId,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) Long entityId,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "1000") int limit,
      @RequestParam(defaultValue = "full") String profile) {
    boolean redacted = switch (profile.strip().toLowerCase(java.util.Locale.ROOT)) {
      case "full" -> false;
      case "redacted" -> true;
      default -> throw new ApiException(400, "INVALID_PROFILE", "Profile must be full or redacted.");
    };
    var filters = new AuditEventQueryService.Filters(
        eventType, actorId, entityType, entityId, source, from, to);
    List<AuditEvent> rows = events.export(filters, limit);
    long departmentId = DepartmentContext.id();
    var csv = new StringBuilder(
        redacted
            ? "Department ID,Audit ID,Actor,Event type,Entity type,Source,Timestamp\r\n"
            : "Department ID,Audit ID,Actor ID,Event type,Entity type,Entity ID,Source,Timestamp,Metadata\r\n");
    // Redacted actors become A1, A2, ... in order of appearance: consistent within one export, but
    // not linkable to accounts or across exports.
    Map<Long, String> pseudonyms = new LinkedHashMap<>();
    for (AuditEvent event : rows) {
      csv.append(departmentId).append(',').append(event.getId()).append(',');
      if (redacted) {
        String actor = event.getUserId() == null
            ? ""
            : pseudonyms.computeIfAbsent(event.getUserId(), id -> "A" + (pseudonyms.size() + 1));
        csv.append(actor).append(',')
            .append(csvText(event.getEventType())).append(',')
            .append(csvText(event.getEntityType())).append(',')
            .append(csvText(event.getSource())).append(',')
            .append(event.getTimestamp()).append("\r\n");
      } else {
        csv.append(event.getUserId() == null ? "" : event.getUserId()).append(',')
            .append(csvText(event.getEventType())).append(',')
            .append(csvText(event.getEntityType())).append(',')
            .append(event.getEntityId() == null ? "" : event.getEntityId()).append(',')
            .append(csvText(event.getSource())).append(',')
            .append(event.getTimestamp()).append(',')
            .append(csvText(event.getMetadata())).append("\r\n");
      }
    }

    Map<String, Object> auditFilters = new LinkedHashMap<>();
    auditFilters.put("export", "audit.csv");
    auditFilters.put("eventType", eventType);
    auditFilters.put("actorId", actorId);
    auditFilters.put("entityType", entityType);
    auditFilters.put("entityId", entityId);
    auditFilters.put("source", source);
    auditFilters.put("from", from);
    auditFilters.put("to", to);
    auditFilters.put("limit", limit);
    auditFilters.put("rows", rows.size());
    auditFilters.put("profile", redacted ? "redacted" : "full");
    if (redacted) auditFilters.put("removedFields", String.join("+", REDACTED_FIELDS));
    audit.log("DATA_EXPORTED", "Audit", null, "UI", auditFilters);

    var response = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .header("X-Audit-Export-Profile", redacted ? "redacted" : "full")
        .header("Content-Disposition", "attachment; filename=audit-department-" + departmentId
            + (redacted ? "-redacted" : "") + ".csv");
    if (redacted) response.header("X-Redacted-Fields", String.join(",", REDACTED_FIELDS));
    return response.body(csv.toString());
  }

  private static String csvText(String value) {
    if (value == null) return "\"\"";
    int index = 0;
    while (index < value.length()) {
      char c = value.charAt(index);
      if (!Character.isWhitespace(c) && !Character.isISOControl(c)) break;
      index++;
    }
    if (index < value.length()) {
      char first = value.charAt(index);
      if (first == '=' || first == '+' || first == '-' || first == '@') value = "'" + value;
    }
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
