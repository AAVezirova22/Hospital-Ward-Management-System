package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deployment-configured retention for operational metadata (#345). Administrators preview what a
 * policy would remove from their department, then apply it with an explicit confirmation. Patient,
 * admission and procedure records are never touched. Applying writes a {@code RETENTION_APPLIED}
 * audit event with the counts removed.
 */
@Service
public class RetentionService {
  public static final String CONFIRMATION = "APPLY RETENTION";

  /** One retention category: which rows are old, how to count and delete them. */
  private record Category(String name, String description, int days, String from, String olderThan) {}

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;
  private final List<Category> categories;

  public RetentionService(
      JdbcTemplate jdbc,
      Actor actor,
      AuditService audit,
      @Value("${app.retention.audit-days:0}") int auditDays,
      @Value("${app.retention.ai-interaction-days:90}") int aiInteractionDays,
      @Value("${app.retention.ai-action-days:90}") int aiActionDays,
      @Value("${app.retention.security-event-days:365}") int securityEventDays) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
    this.categories =
        List.of(
            category("AUDIT_EVENTS", "Audit events", auditDays, "RETENTION_AUDIT_DAYS",
                "audit_events where department_id = ? and timestamp < ?"),
            category("AI_INTERACTIONS", "Assistant request metadata", aiInteractionDays, "RETENTION_AI_INTERACTION_DAYS",
                "ai_interactions where department_id = ? and started_at < ?"),
            category("AI_ACTIONS", "Resolved assistant proposals", aiActionDays, "RETENTION_AI_ACTION_DAYS",
                "ai_pending_actions where department_id = ? and status <> 'PENDING' and created_at < ?"),
            category("SECURITY_EVENTS", "Resolved or dismissed security review entries", securityEventDays,
                "RETENTION_SECURITY_EVENT_DAYS",
                "security_events where department_id = ? and status in ('RESOLVED', 'DISMISSED') and last_seen_at < ?"));
  }

  private static Category category(String name, String description, int days, String variable, String clause) {
    if (days < 0 || days > 36500)
      throw new IllegalStateException(variable + " must be between 0 (keep forever) and 36500 days.");
    int where = clause.indexOf(" where ");
    return new Category(name, description, days, clause.substring(0, where), clause.substring(where + 7));
  }

  /** Dry run: what the configured policy would remove from the active department now. */
  @Transactional(readOnly = true)
  public Map<String, Object> preview() {
    actor.admin();
    return report(false);
  }

  /** Removes everything the policy covers in the active department and audits the counts. */
  @Transactional
  public Map<String, Object> apply(String confirmation) {
    actor.admin();
    if (!CONFIRMATION.equals(confirmation))
      throw new ApiException(400, "CONFIRMATION_REQUIRED", "Type " + CONFIRMATION + " to delete old records.");
    // Audit events are append-only; retention is one of the two sanctioned maintenance paths.
    jdbc.queryForObject("select set_config('hospital.audit_maintenance', 'on', true)", String.class);
    var result = report(true);
    var counts = new LinkedHashMap<String, Object>();
    for (var row : castRows(result.get("categories"))) counts.put(String.valueOf(row.get("category")), row.get("removed"));
    jdbc.queryForObject("select set_config('hospital.audit_maintenance', 'off', true)", String.class);
    audit.log("RETENTION_APPLIED", "Department", DepartmentContext.id(), "UI", counts);
    return result;
  }

  private Map<String, Object> report(boolean delete) {
    long departmentId = DepartmentContext.id();
    Instant now = Instant.now();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Category category : categories) {
      var row = new LinkedHashMap<String, Object>();
      row.put("category", category.name());
      row.put("description", category.description());
      row.put("retentionDays", category.days());
      if (category.days() == 0) {
        row.put("cutoff", null);
        row.put(delete ? "removed" : "eligible", 0);
      } else {
        Instant cutoff = now.minus(category.days(), ChronoUnit.DAYS);
        row.put("cutoff", cutoff);
        Timestamp at = Timestamp.from(cutoff);
        if (delete) {
          row.put("removed", jdbc.update(
              "delete from " + category.from() + " where " + category.olderThan(), departmentId, at));
        } else {
          row.put("eligible", jdbc.queryForObject(
              "select count(*) from " + category.from() + " where " + category.olderThan(), Long.class, departmentId, at));
        }
      }
      rows.add(row);
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("departmentId", departmentId);
    result.put("dryRun", !delete);
    result.put("evaluatedAt", now);
    result.put("categories", rows);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> castRows(Object rows) {
    return (List<Map<String, Object>>) rows;
  }
}
