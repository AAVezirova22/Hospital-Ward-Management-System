package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.NotificationPolicyInput;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-department notification rules (#320). A department without a saved row uses the deployment
 * defaults, and the same warning/critical defaults as the operations dashboard.
 */
@Service
public class NotificationPolicies {
  /** Delivery channels that exist today. Email delivery can join once the reminder mailer (#453) lands. */
  public static final List<String> CHANNELS = List.of("IN_APP");

  public record Policy(
      long departmentId,
      long version,
      boolean configured,
      boolean capacityAlertsEnabled,
      boolean activityNoticesEnabled,
      int warningPercent,
      int criticalPercent,
      boolean escalationEnabled,
      String escalationMinSeverity,
      int acknowledgementMinutes,
      String escalationRole,
      int retentionDays,
      List<String> channels,
      Instant updatedAt,
      Long updatedBy) {}

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;
  private final int defaultWarning;
  private final int defaultCritical;

  public NotificationPolicies(
      JdbcTemplate jdbc,
      Actor actor,
      AuditService audit,
      @Value("${app.operations.warning-percent:75}") double warning,
      @Value("${app.operations.critical-percent:90}") double critical) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
    this.defaultWarning = (int) Math.round(warning);
    this.defaultCritical = (int) Math.round(critical);
  }

  /** Policy of the department open in the current request. */
  public Policy current() {
    return load(requireDepartment());
  }

  /** Callers pass an explicit id because background jobs have no request department. */
  public Policy load(long departmentId) {
    var rows =
        jdbc.query(
            "select * from notification_policies where department_id = ?",
            (rs, n) -> map(rs),
            departmentId);
    if (!rows.isEmpty()) return rows.getFirst();
    return new Policy(
        departmentId, 0, false, true, true, defaultWarning, defaultCritical, true, "CRITICAL", 30,
        "ADMIN", 14, CHANNELS, null, null);
  }

  @Transactional
  public Policy update(NotificationPolicyInput in) {
    actor.admin();
    long departmentId = requireDepartment();
    if (in.warningPercent() >= in.criticalPercent())
      throw new ApiException(
          400, "INVALID_POLICY", "The warning threshold must be lower than the critical threshold.");
    long expected = in.version() == null ? 0 : in.version();
    long userId = actor.user().getId();
    int saved =
        expected == 0
            ? jdbc.update(
                """
                insert into notification_policies(
                    department_id, capacity_alerts_enabled, activity_notices_enabled, warning_percent,
                    critical_percent, escalation_enabled, escalation_min_severity, acknowledgement_minutes,
                    escalation_role, retention_days, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?)
                on conflict (department_id) do nothing
                """,
                departmentId, in.capacityAlertsEnabled(), in.activityNoticesEnabled(),
                in.warningPercent(), in.criticalPercent(), in.escalationEnabled(),
                in.escalationMinSeverity(), in.acknowledgementMinutes(), in.escalationRole(),
                in.retentionDays(), userId)
            : jdbc.update(
                """
                update notification_policies
                   set capacity_alerts_enabled = ?, activity_notices_enabled = ?, warning_percent = ?,
                       critical_percent = ?, escalation_enabled = ?, escalation_min_severity = ?,
                       acknowledgement_minutes = ?, escalation_role = ?, retention_days = ?,
                       version = version + 1, updated_at = now(), updated_by = ?
                 where department_id = ? and version = ?
                """,
                in.capacityAlertsEnabled(), in.activityNoticesEnabled(), in.warningPercent(),
                in.criticalPercent(), in.escalationEnabled(), in.escalationMinSeverity(),
                in.acknowledgementMinutes(), in.escalationRole(), in.retentionDays(), userId,
                departmentId, expected);
    if (saved != 1)
      throw ApiException.conflict("STALE_STATE", "This record changed. Refresh before continuing.");
    var changes = new LinkedHashMap<String, Object>();
    changes.put("capacityAlerts", in.capacityAlertsEnabled());
    changes.put("activityNotices", in.activityNoticesEnabled());
    changes.put("warningPercent", in.warningPercent());
    changes.put("criticalPercent", in.criticalPercent());
    changes.put("escalation", in.escalationEnabled());
    changes.put("escalationMinSeverity", in.escalationMinSeverity());
    changes.put("acknowledgementMinutes", in.acknowledgementMinutes());
    changes.put("escalationRole", in.escalationRole());
    changes.put("retentionDays", in.retentionDays());
    audit.log("NOTIFICATION_POLICY_UPDATED", "Department", departmentId, "UI", changes);
    return load(departmentId);
  }

  static long requireDepartment() {
    long departmentId = DepartmentContext.id();
    if (departmentId <= 0)
      throw new ApiException(403, "DEPARTMENT_REQUIRED", "Open a department before managing notifications.");
    return departmentId;
  }

  private static Policy map(ResultSet rs) throws SQLException {
    Timestamp updatedAt = rs.getTimestamp("updated_at");
    long updatedById = rs.getLong("updated_by");
    Long updatedBy = rs.wasNull() ? null : updatedById;
    return new Policy(
        rs.getLong("department_id"),
        rs.getLong("version"),
        true,
        rs.getBoolean("capacity_alerts_enabled"),
        rs.getBoolean("activity_notices_enabled"),
        rs.getInt("warning_percent"),
        rs.getInt("critical_percent"),
        rs.getBoolean("escalation_enabled"),
        rs.getString("escalation_min_severity"),
        rs.getInt("acknowledgement_minutes"),
        rs.getString("escalation_role"),
        rs.getInt("retention_days"),
        CHANNELS,
        updatedAt == null ? null : updatedAt.toInstant(),
        updatedBy);
  }
}
