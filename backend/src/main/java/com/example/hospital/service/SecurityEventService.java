package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security review queue for department administrators (#352). Failed sign-ins, repeated access
 * denials, rejected join codes, role changes and join-code handling are grouped per department so
 * an administrator reviews one row per pattern, acknowledges it and tracks the investigation.
 * Rows hold who and what happened, never passwords, source addresses or clinical data.
 */
@Service
public class SecurityEventService {
  public static final Set<String> STATUSES = Set.of("OPEN", "INVESTIGATING", "RESOLVED", "DISMISSED");

  /** How an audited action becomes a queue entry. */
  private record Rule(String category, String severity, boolean escalates, boolean perActor, ChronoUnit bucket) {}

  private static final Map<String, Rule> RULES =
      Map.of(
          "ACCESS_DENIED", new Rule("ACCESS_DENIED", "INFO", true, true, ChronoUnit.DAYS),
          "JOIN_CODE_REJECTED", new Rule("JOIN_CODE", "INFO", true, true, ChronoUnit.DAYS),
          "JOIN_CODE_ROTATED", new Rule("JOIN_CODE", "INFO", false, false, ChronoUnit.DAYS),
          "JOIN_CODE_VIEWED", new Rule("JOIN_CODE", "INFO", false, false, ChronoUnit.DAYS),
          "DEPARTMENT_ROLE_GRANTED", new Rule("ROLE_CHANGE", "WARNING", false, true, ChronoUnit.HOURS),
          "USER_SAVED", new Rule("ROLE_CHANGE", "WARNING", false, true, ChronoUnit.HOURS),
          "DEPARTMENT_MEMBER_REVOKED", new Rule("ROLE_CHANGE", "WARNING", false, true, ChronoUnit.HOURS),
          "HOSPITAL_MEMBER_REVOKED", new Rule("ROLE_CHANGE", "WARNING", false, true, ChronoUnit.HOURS),
          "HOSPITAL_OWNER_GRANTED", new Rule("ROLE_CHANGE", "CRITICAL", false, true, ChronoUnit.HOURS));

  private static final String COLUMNS =
      "e.id, e.category, e.type, e.severity, e.subject, e.detail, e.occurrences, e.first_seen_at,"
          + " e.last_seen_at, e.status, e.status_note, e.acknowledged_at, ack.username, e.status_changed_at,"
          + " changed.username";

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;

  public SecurityEventService(JdbcTemplate jdbc, Actor actor, AuditService audit) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
  }

  public static boolean tracked(String auditEvent) {
    return RULES.containsKey(auditEvent);
  }

  /** Adds an audited action to the queue. Runs after the audited change commits. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditService.Recorded event) {
    Rule rule = RULES.get(event.event());
    if (rule == null || event.departmentId() <= 0) return;
    String actorName = event.userId() == null ? null : username(event.userId());
    Instant now = Instant.now();
    String scope =
        rule.perActor()
            ? "user=" + event.userId()
            : event.entity() + "=" + (event.entityId() == null ? "-" : event.entityId());
    String key = event.event() + ":" + scope + ":" + now.truncatedTo(rule.bucket());
    String target = event.entity() + (event.entityId() == null ? "" : " #" + event.entityId());
    upsert(
        event.departmentId(),
        rule.category(),
        event.event(),
        rule.severity(),
        rule.escalates(),
        event.userId(),
        actorName,
        describe(event.event(), actorName, target),
        key,
        now);
  }

  /** Counts a failed or blocked sign-in against every department the named account belongs to. */
  @Transactional
  public void failedLogin(String username) {
    if (username == null || username.isBlank() || username.length() > 64) return;
    var accounts =
        jdbc.queryForList(
            "select id, username from app_users where lower(username) = lower(?) and role <> 'PATIENT'",
            username.strip());
    if (accounts.isEmpty()) return; // Unknown names stay with login backoff; no queue noise.
    long userId = ((Number) accounts.getFirst().get("id")).longValue();
    String name = String.valueOf(accounts.getFirst().get("username"));
    Instant now = Instant.now();
    String key = "LOGIN_FAILED:user=" + userId + ":" + now.truncatedTo(ChronoUnit.DAYS);
    for (Long departmentId :
        jdbc.queryForList(
            "select department_id from department_memberships where user_id = ?", Long.class, userId)) {
      upsert(
          departmentId,
          "FAILED_LOGIN",
          "LOGIN_FAILED",
          "INFO",
          true,
          userId,
          name,
          "Failed or blocked sign-in attempts for " + name + " today.",
          key,
          now);
    }
  }

  public Map<String, Object> inbox(String status, boolean includeInfo, int page, int size) {
    long departmentId = DepartmentContext.id();
    int safeSize = Math.min(Math.max(size, 1), 100);
    int safePage = Math.max(page, 0);
    String wanted = status == null ? "ACTIVE" : status.strip().toUpperCase(Locale.ROOT);
    String statusFilter =
        switch (wanted) {
          case "ACTIVE" -> " and e.status in ('OPEN', 'INVESTIGATING')";
          case "ALL" -> "";
          default -> {
            if (!STATUSES.contains(wanted))
              throw new ApiException(400, "INVALID_STATUS", "Status must be ACTIVE, ALL, OPEN, INVESTIGATING, RESOLVED or DISMISSED.");
            yield " and e.status = '" + wanted + "'";
          }
        };
    String severityFilter = includeInfo ? "" : " and e.severity <> 'INFO'";
    String from =
        " from security_events e left join app_users ack on ack.id = e.acknowledged_by"
            + " left join app_users changed on changed.id = e.status_changed_by"
            + " where e.department_id = ?" + statusFilter + severityFilter;
    Long total = jdbc.queryForObject("select count(*)" + from, Long.class, departmentId);
    List<Map<String, Object>> events =
        jdbc.query(
            "select " + COLUMNS + from
                + " order by case e.severity when 'CRITICAL' then 0 when 'WARNING' then 1 else 2 end,"
                + " e.last_seen_at desc, e.id desc limit ? offset ?",
            (rs, row) -> view(rs),
            departmentId,
            safeSize,
            (long) safePage * safeSize);
    var counts =
        jdbc.queryForMap(
            """
            select count(*) filter (where status = 'OPEN') as open,
                   count(*) filter (where status = 'INVESTIGATING') as investigating,
                   count(*) filter (where status in ('OPEN', 'INVESTIGATING') and severity = 'CRITICAL') as critical,
                   count(*) filter (where status = 'OPEN' and acknowledged_at is null) as unacknowledged
            from security_events where department_id = ?
            """,
            departmentId);
    var result = new LinkedHashMap<String, Object>();
    result.put("page", safePage);
    result.put("size", safeSize);
    result.put("total", total == null ? 0 : total);
    result.put("counts", counts);
    result.put("events", events);
    return result;
  }

  @Transactional
  public Map<String, Object> acknowledge(long id) {
    requireInDepartment(id);
    jdbc.update(
        "update security_events set acknowledged_by = ?, acknowledged_at = now() where id = ? and acknowledged_at is null",
        actor.user().getId(),
        id);
    audit.log("SECURITY_EVENT_ACKNOWLEDGED", "SecurityEvent", id, "UI");
    return get(id);
  }

  @Transactional
  public Map<String, Object> updateStatus(long id, String status, String note) {
    requireInDepartment(id);
    String next = status == null ? "" : status.strip().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(next))
      throw new ApiException(400, "INVALID_STATUS", "Status must be OPEN, INVESTIGATING, RESOLVED or DISMISSED.");
    String text = note == null || note.isBlank() ? null : note.strip();
    if (text != null && text.length() > 300)
      throw new ApiException(400, "VALIDATION_ERROR", "Keep the note under 300 characters.");
    try {
      jdbc.update(
          """
          update security_events
             set status = ?, status_note = ?, status_changed_by = ?, status_changed_at = now(),
                 acknowledged_by = coalesce(acknowledged_by, ?), acknowledged_at = coalesce(acknowledged_at, now())
           where id = ?
          """,
          next,
          text,
          actor.user().getId(),
          actor.user().getId(),
          id);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw ApiException.conflict(
          "SECURITY_EVENT_REOPENED", "A newer open entry already tracks this pattern. Update that entry instead.");
    }
    audit.log("SECURITY_EVENT_UPDATED", "SecurityEvent", id, "UI", Map.of("status", next));
    return get(id);
  }

  private Map<String, Object> get(long id) {
    return jdbc.queryForObject(
        "select " + COLUMNS
            + " from security_events e left join app_users ack on ack.id = e.acknowledged_by"
            + " left join app_users changed on changed.id = e.status_changed_by where e.id = ?",
        (rs, row) -> view(rs),
        id);
  }

  private void requireInDepartment(long id) {
    Boolean found =
        jdbc.queryForObject(
            "select exists (select 1 from security_events where id = ? and department_id = ?)",
            Boolean.class,
            id,
            DepartmentContext.id());
    if (!Boolean.TRUE.equals(found)) throw ApiException.missing();
  }

  private void upsert(
      long departmentId,
      String category,
      String type,
      String severity,
      boolean escalates,
      Long subjectUserId,
      String subject,
      String detail,
      String key,
      Instant at) {
    jdbc.update(
        """
        insert into security_events(department_id, category, type, severity, escalates, subject_user_id,
            subject, detail, occurrences, dedupe_key, first_seen_at, last_seen_at, status)
        values (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 'OPEN')
        on conflict (department_id, dedupe_key) where status in ('OPEN', 'INVESTIGATING')
        do update set
          occurrences = security_events.occurrences + 1,
          last_seen_at = excluded.last_seen_at,
          detail = excluded.detail,
          severity = case
            when security_events.escalates then
              case when security_events.occurrences + 1 >= 10 then 'CRITICAL'
                   when security_events.occurrences + 1 >= 3 then 'WARNING'
                   else 'INFO' end
            when 'CRITICAL' in (excluded.severity, security_events.severity) then 'CRITICAL'
            when 'WARNING' in (excluded.severity, security_events.severity) then 'WARNING'
            else 'INFO' end
        """,
        departmentId,
        category,
        type,
        severity,
        escalates,
        subjectUserId,
        subject,
        detail,
        key,
        Timestamp.from(at),
        Timestamp.from(at));
  }

  private String username(long userId) {
    var names = jdbc.queryForList("select username from app_users where id = ?", String.class, userId);
    return names.isEmpty() ? null : names.getFirst();
  }

  private static String describe(String event, String actorName, String target) {
    String who = actorName == null ? "An account" : actorName;
    return switch (event) {
      case "ACCESS_DENIED" -> who + " was refused access to protected operations.";
      case "JOIN_CODE_REJECTED" -> who + " entered invalid or expired join codes.";
      case "JOIN_CODE_ROTATED" -> who + " replaced the join code of " + target + ".";
      case "JOIN_CODE_VIEWED" -> who + " revealed the join code of " + target + ".";
      case "HOSPITAL_OWNER_GRANTED" -> who + " granted hospital ownership on " + target + ".";
      case "USER_SAVED" -> who + " changed account settings (" + target + ").";
      case "DEPARTMENT_MEMBER_REVOKED", "HOSPITAL_MEMBER_REVOKED" -> who + " removed a member from " + target + ".";
      default -> who + " changed roles in " + target + ".";
    };
  }

  private static Map<String, Object> view(java.sql.ResultSet rs) throws java.sql.SQLException {
    var row = new LinkedHashMap<String, Object>();
    row.put("id", rs.getLong(1));
    row.put("category", rs.getString(2));
    row.put("type", rs.getString(3));
    row.put("severity", rs.getString(4));
    row.put("subject", rs.getString(5));
    row.put("detail", rs.getString(6));
    row.put("occurrences", rs.getInt(7));
    row.put("firstSeenAt", instant(rs.getTimestamp(8)));
    row.put("lastSeenAt", instant(rs.getTimestamp(9)));
    row.put("status", rs.getString(10));
    row.put("statusNote", rs.getString(11));
    row.put("acknowledgedAt", instant(rs.getTimestamp(12)));
    row.put("acknowledgedBy", rs.getString(13));
    row.put("statusChangedAt", instant(rs.getTimestamp(14)));
    row.put("statusChangedBy", rs.getString(15));
    return row;
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
