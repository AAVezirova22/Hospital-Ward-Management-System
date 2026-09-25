package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side notification inbox.
 *
 * <ul>
 *   <li>#303 alerts and recent-action notices are stored per department and survive reloads;
 *   <li>#304 read state is tracked per account;
 *   <li>#317 one open alert per underlying condition, updated in place as the condition changes;
 *   <li>#310 alerts at or above the policy severity must be acknowledged before a deadline, otherwise
 *       they are escalated once to the configured role.
 * </ul>
 *
 * Queries are written against explicit department ids because background jobs run without a request
 * department, so the Hibernate department filter does not apply here.
 */
@Service
public class NotificationService {
  public static final String CAPACITY = "CAPACITY";
  public static final String ACTIVITY = "ACTIVITY";

  /** Audit events that become a personal "recent action" notice for the account that made them. */
  private static final Map<String, String> ACTIVITY_TITLES =
      Map.ofEntries(
          Map.entry("ADMISSION_CREATED", "Admission created"),
          Map.entry("ROOM_TRANSFERRED", "Patient transferred"),
          Map.entry("PATIENT_DISCHARGED", "Patient discharged"),
          Map.entry("DOCTOR_ASSIGNED", "Attending doctor changed"),
          Map.entry("PROCEDURE_RECORDED", "Procedure recorded"),
          Map.entry("DISCHARGE_PLANNED", "Expected discharge updated"),
          Map.entry("ROOM_SAVED", "Room saved"),
          Map.entry("DOCTOR_SAVED", "Doctor saved"),
          Map.entry("PROCEDURE_SAVED", "Procedure saved"),
          Map.entry("USER_SAVED", "Account saved"),
          Map.entry("HOSPITAL_CREATED", "Hospital created"),
          Map.entry("DEPARTMENT_CREATED", "Department created"),
          Map.entry("NOTIFICATION_POLICY_UPDATED", "Notification policy updated"));

  /** Audit events after which department capacity may have changed. */
  static final Set<String> CAPACITY_EVENTS =
      Set.of(
          "ADMISSION_CREATED", "ROOM_ASSIGNED", "ROOM_TRANSFERRED", "PATIENT_DISCHARGED", "ROOM_SAVED",
          "NOTIFICATION_POLICY_UPDATED", "BED_HOLD_CREATED", "BED_HOLD_CANCELLED");

  /** First key of the Postgres advisory lock that serializes capacity evaluation per department. */
  private static final int EVALUATION_LOCK = 310317;

  private static final String SELECT =
      "select n.*, (r.user_id is not null) as is_read from notifications n"
          + " left join notification_reads r on r.notification_id = n.id and r.user_id = ?";
  private static final String VISIBLE =
      " where n.department_id = ? and (n.recipient_user_id is null or n.recipient_user_id = ?)"
          + " and (n.expires_at is null or n.expires_at > ?)";
  private static final String ORDER =
      " order by case when n.status = 'OPEN' and n.escalated_at is not null and n.acknowledged_at is null"
          + " then 0 when n.status = 'OPEN' then 1 else 2 end, n.last_seen_at desc, n.id desc";

  public record Notice(
      long id,
      long version,
      String category,
      String type,
      String severity,
      String status,
      String title,
      String detail,
      String sourceType,
      Long sourceId,
      boolean personal,
      boolean read,
      Instant firstSeenAt,
      Instant lastSeenAt,
      Instant resolvedAt,
      Instant expiresAt,
      boolean requiresAcknowledgement,
      Instant acknowledgementDueAt,
      Instant acknowledgedAt,
      Long acknowledgedBy,
      Instant escalatedAt,
      String escalatedToRole,
      boolean escalatedToYou) {}

  public record Inbox(
      List<Notice> items,
      int page,
      int size,
      long totalElements,
      int totalPages,
      boolean hasNext,
      Integer nextPage,
      long unread) {}

  public record Counts(long unread, long unreadAlerts, long awaitingAcknowledgement, long escalatedToYou) {}

  private record Viewer(long departmentId, long userId, String role) {}

  private record Candidate(
      String key, String type, String severity, String sourceType, long sourceId, String title, String detail) {}

  private record OpenAlert(long id, String key, String severity, String title, String detail, Timestamp dueAt) {}

  private record RoomLoad(long id, String number, int beds, long occupied) {}

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final NotificationPolicies policies;
  private final AuditService audit;
  private final ApplicationEventPublisher publisher;
  private final TaskReminderService push;

  public NotificationService(
      JdbcTemplate jdbc,
      Actor actor,
      NotificationPolicies policies,
      AuditService audit,
      ApplicationEventPublisher publisher,
      TaskReminderService push) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.policies = policies;
    this.audit = audit;
    this.publisher = publisher;
    this.push = push;
  }

  // ---------------------------------------------------------------- inbox (#303, #304)

  public Inbox inbox(int page, int size, String category, String status, boolean unreadOnly) {
    if (page < 0 || size < 1)
      throw new ApiException(400, "VALIDATION_ERROR", "Check the notification page values.");
    int pageSize = Math.min(size, 100);
    String wantedCategory = option(category, Set.of(CAPACITY, ACTIVITY), "Choose a valid notification category.");
    String wantedStatus = option(status, Set.of("OPEN", "RESOLVED", "INFO"), "Choose a valid notification status.");
    var viewer = viewer();
    if (viewer.departmentId() <= 0) return new Inbox(List.of(), page, pageSize, 0, 0, false, null, 0);

    var where = new StringBuilder(VISIBLE);
    var args = visibleArgs(viewer);
    if (wantedCategory != null) {
      where.append(" and n.category = ?");
      args.add(wantedCategory);
    }
    if (wantedStatus != null) {
      where.append(" and n.status = ?");
      args.add(wantedStatus);
    }
    if (unreadOnly) where.append(" and r.user_id is null");

    Long total =
        jdbc.queryForObject(
            "select count(*) from notifications n left join notification_reads r"
                + " on r.notification_id = n.id and r.user_id = ?" + where,
            Long.class,
            args.toArray());
    long totalElements = total == null ? 0 : total;
    var pageArgs = new ArrayList<>(args);
    pageArgs.add(pageSize);
    pageArgs.add((long) page * pageSize);
    var items =
        jdbc.query(
            SELECT + where + ORDER + " limit ? offset ?",
            (rs, n) -> notice(rs, viewer.role()),
            pageArgs.toArray());
    int totalPages = (int) ((totalElements + pageSize - 1) / pageSize);
    boolean hasNext = (long) (page + 1) * pageSize < totalElements;
    return new Inbox(
        items, page, pageSize, totalElements, totalPages, hasNext, hasNext ? page + 1 : null,
        counts(viewer).unread());
  }

  public Counts counts() {
    var viewer = viewer();
    if (viewer.departmentId() <= 0) return new Counts(0, 0, 0, 0);
    return counts(viewer);
  }

  public Notice get(long id) {
    return find(viewer(), id);
  }

  @Transactional
  public Notice markRead(long id) {
    var viewer = viewer();
    find(viewer, id);
    jdbc.update(
        "insert into notification_reads(notification_id, user_id) values (?, ?) on conflict do nothing",
        id, viewer.userId());
    return find(viewer, id);
  }

  @Transactional
  public Notice markUnread(long id) {
    var viewer = viewer();
    find(viewer, id);
    jdbc.update("delete from notification_reads where notification_id = ? and user_id = ?", id, viewer.userId());
    return find(viewer, id);
  }

  @Transactional
  public Map<String, Object> markAllRead(String category) {
    String wantedCategory = option(category, Set.of(CAPACITY, ACTIVITY), "Choose a valid notification category.");
    var viewer = viewer();
    if (viewer.departmentId() <= 0) return Map.of("marked", 0, "unread", 0);
    var args = new ArrayList<Object>();
    args.add(viewer.userId());
    args.add(viewer.departmentId());
    args.add(viewer.userId());
    args.add(Timestamp.from(Instant.now()));
    String sql =
        "insert into notification_reads(notification_id, user_id) select n.id, ? from notifications n"
            + " where n.department_id = ? and (n.recipient_user_id is null or n.recipient_user_id = ?)"
            + " and (n.expires_at is null or n.expires_at > ?)";
    if (wantedCategory != null) {
      sql += " and n.category = ?";
      args.add(wantedCategory);
    }
    int marked = jdbc.update(sql + " on conflict do nothing", args.toArray());
    return Map.of("marked", marked, "unread", counts(viewer).unread());
  }

  // ---------------------------------------------------------------- acknowledgement (#310)

  @Transactional
  public Notice acknowledge(long id) {
    actor.staff();
    var viewer = viewer();
    var current = find(viewer, id);
    if (!current.requiresAcknowledgement())
      throw ApiException.conflict("NOT_ACKNOWLEDGEABLE", "This notice does not need acknowledgement.");
    if (!"OPEN".equals(current.status()))
      throw ApiException.conflict("NOTIFICATION_RESOLVED", "This alert has already been resolved.");
    int updated =
        jdbc.update(
            "update notifications set acknowledged_at = ?, acknowledged_by = ?, version = version + 1,"
                + " updated_at = now() where id = ? and department_id = ? and status = 'OPEN'"
                + " and acknowledged_at is null",
            Timestamp.from(Instant.now()), viewer.userId(), id, viewer.departmentId());
    if (updated != 1) {
      var latest = find(viewer, id);
      if (!"OPEN".equals(latest.status()))
        throw ApiException.conflict("NOTIFICATION_RESOLVED", "This alert has already been resolved.");
      throw ApiException.conflict("ALREADY_ACKNOWLEDGED", "This alert has already been acknowledged.");
    }
    jdbc.update(
        "insert into notification_reads(notification_id, user_id) values (?, ?) on conflict do nothing",
        id, viewer.userId());
    audit.log("NOTIFICATION_ACKNOWLEDGED", "Notification", id, "UI");
    return find(viewer, id);
  }

  // ---------------------------------------------------------------- producers

  /** Stores a personal recent-action notice (#303). Runs after the audited change has committed. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordActivity(AuditService.Recorded event) {
    String title = ACTIVITY_TITLES.get(event.event());
    if (title == null || event.userId() == null || event.departmentId() <= 0) return;
    var policy = policies.load(event.departmentId());
    if (!policy.activityNoticesEnabled()) return;
    var now = Instant.now();
    String subject =
        event.entity() == null ? "" : event.entity() + (event.entityId() == null ? "" : " #" + event.entityId());
    String detail = "AI".equals(event.source()) ? subject + " (confirmed through the assistant)" : subject;
    Long noticeId = jdbc.queryForObject(
        "insert into notifications(department_id, recipient_user_id, category, type, severity, source_type,"
            + " source_id, title, detail, status, first_seen_at, last_seen_at, expires_at)"
            + " values (?, ?, 'ACTIVITY', ?, 'INFO', ?, ?, ?, ?, 'INFO', ?, ?, ?) returning id",
        Long.class,
        event.departmentId(), event.userId(), event.event(), event.entity(), event.entityId(), title,
        detail.isBlank() ? null : detail, Timestamp.from(now), Timestamp.from(now),
        Timestamp.from(now.plus(policy.retentionDays(), ChronoUnit.DAYS)));
    push.queueOperationalNotice(event.departmentId(), noticeId, event.userId());
  }

  /**
   * Brings the department's capacity alerts in line with current occupancy (#317): one open row per
   * condition, updated in place, resolved when the condition clears. Returns whether anything changed.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean evaluateCapacity(long departmentId) {
    if (departmentId <= 0) return false;
    // Concurrent evaluations of one department would otherwise race between reading and writing.
    jdbc.query(
        "select 1 from (select pg_advisory_xact_lock(?, ?)) locked",
        (rs, n) -> 1,
        EVALUATION_LOCK, (int) departmentId);
    var policy = policies.load(departmentId);
    var now = Instant.now();
    Map<String, Candidate> wanted = policy.capacityAlertsEnabled() ? candidates(departmentId, policy) : Map.of();
    var open = new HashMap<String, OpenAlert>();
    jdbc.query(
            "select id, dedupe_key, severity, title, detail, acknowledgement_due_at from notifications"
                + " where department_id = ? and category = 'CAPACITY' and status = 'OPEN'",
            (rs, n) ->
                new OpenAlert(
                    rs.getLong("id"), rs.getString("dedupe_key"), rs.getString("severity"),
                    rs.getString("title"), rs.getString("detail"), rs.getTimestamp("acknowledgement_due_at")),
            departmentId)
        .forEach(alert -> open.put(alert.key(), alert));

    boolean changed = false;
    for (var candidate : wanted.values()) {
      var existing = open.remove(candidate.key());
      Timestamp due =
          needsAcknowledgement(candidate.severity(), policy)
              ? Timestamp.from(now.plus(policy.acknowledgementMinutes(), ChronoUnit.MINUTES))
              : null;
      if (existing == null) {
        Long noticeId = jdbc.queryForObject(
            "insert into notifications(department_id, category, type, severity, dedupe_key, source_type,"
                + " source_id, title, detail, status, first_seen_at, last_seen_at, acknowledgement_due_at)"
                + " values (?, 'CAPACITY', ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?) returning id",
            Long.class,
            departmentId, candidate.type(), candidate.severity(), candidate.key(), candidate.sourceType(),
            candidate.sourceId(), candidate.title(), candidate.detail(), Timestamp.from(now),
            Timestamp.from(now), due);
        push.queueOperationalNotice(departmentId, noticeId, null);
        changed = true;
        continue;
      }
      Timestamp keptDue = existing.dueAt() != null ? existing.dueAt() : due;
      boolean different =
          !candidate.severity().equals(existing.severity())
              || !candidate.title().equals(existing.title())
              || !Objects.equals(candidate.detail(), existing.detail())
              || !Objects.equals(keptDue, existing.dueAt());
      jdbc.update(
          "update notifications set last_seen_at = ?, severity = ?, title = ?, detail = ?,"
              + " acknowledgement_due_at = ?, version = version + ?,"
              + " updated_at = case when ? then now() else updated_at end where id = ?",
          Timestamp.from(now), candidate.severity(), candidate.title(), candidate.detail(), keptDue,
          different ? 1 : 0, different, existing.id());
      // A worse condition should be seen again by people who already read the milder one.
      if (rank(candidate.severity()) > rank(existing.severity()))
        jdbc.update("delete from notification_reads where notification_id = ?", existing.id());
      if (rank(candidate.severity()) > rank(existing.severity()))
        push.queueOperationalNotice(departmentId, existing.id(), null);
      changed |= different;
    }
    for (var cleared : open.values()) {
      jdbc.update(
          "update notifications set status = 'RESOLVED', resolved_at = ?, expires_at = ?,"
              + " version = version + 1, updated_at = now() where id = ?",
          Timestamp.from(now), Timestamp.from(now.plus(policy.retentionDays(), ChronoUnit.DAYS)),
          cleared.id());
      changed = true;
    }
    if (changed) publisher.publishEvent(new OperationsStream.Changed(departmentId));
    return changed;
  }

  /**
   * Escalates alerts whose acknowledgement deadline passed (#310). Each alert escalates once: the
   * existing row is marked, its read state is cleared, and a system audit event is written. Returns the
   * number of alerts escalated by this call.
   */
  @Transactional
  public int escalateDue(long departmentId) {
    if (departmentId <= 0) return 0;
    var policy = policies.load(departmentId);
    if (!policy.escalationEnabled()) return 0;
    var now = Timestamp.from(Instant.now());
    var due =
        jdbc.queryForList(
            "select id from notifications where department_id = ? and status = 'OPEN'"
                + " and acknowledged_at is null and escalated_at is null and acknowledgement_due_at <= ?"
                + " order by acknowledgement_due_at, id for update skip locked",
            Long.class,
            departmentId, now);
    int escalated = 0;
    for (long id : due) {
      int updated =
          jdbc.update(
              "update notifications set escalated_at = ?, escalated_to_role = ?, version = version + 1,"
                  + " updated_at = now() where id = ? and status = 'OPEN' and acknowledged_at is null"
                  + " and escalated_at is null",
              now, policy.escalationRole(), id);
      if (updated != 1) continue;
      push.queueOperationalNotice(departmentId, id, null);
      jdbc.update("delete from notification_reads where notification_id = ?", id);
      // No signed-in actor here, so the audit row is written directly with a null user and SYSTEM source.
      jdbc.update(
          "insert into audit_events(department_id, user_id, event_type, entity_type, entity_id, source,"
              + " timestamp, metadata) values (?, null, 'NOTIFICATION_ESCALATED', 'Notification', ?, 'SYSTEM', ?, ?)",
          departmentId, id, now,
          "{event=NOTIFICATION_ESCALATED, entity=Notification, id=" + id + ", escalatedTo="
              + policy.escalationRole() + "}");
      escalated++;
    }
    if (escalated > 0) publisher.publishEvent(new OperationsStream.Changed(departmentId));
    return escalated;
  }

  @Transactional
  public int purgeExpired() {
    return jdbc.update("delete from notifications where expires_at is not null and expires_at <= now()");
  }

  public List<Long> departmentIds() {
    return jdbc.queryForList("select id from departments order by id", Long.class);
  }

  // ---------------------------------------------------------------- helpers

  private Map<String, Candidate> candidates(long departmentId, NotificationPolicies.Policy policy) {
    var rooms =
        jdbc.query(
            """
            select r.id, r.room_number, r.bed_count,
                   (select count(*) from room_assignments ra
                     where ra.room_id = r.id and ra.released_at is null) as occupied
              from rooms r
             where r.department_id = ? and r.active
             order by r.room_number, r.id
            """,
            (rs, n) ->
                new RoomLoad(
                    rs.getLong("id"), rs.getString("room_number"), rs.getInt("bed_count"), rs.getLong("occupied")),
            departmentId);
    var result = new LinkedHashMap<String, Candidate>();
    long beds = 0;
    long occupied = 0;
    for (var room : rooms) {
      beds += room.beds();
      occupied += Math.min(room.occupied(), room.beds());
      if (room.occupied() >= room.beds()) {
        String key = "capacity:room:" + room.id();
        result.put(
            key,
            new Candidate(
                key, "ROOM_FULL", "WARNING", "Room", room.id(), "Room " + room.number() + " is full",
                room.occupied() + " of " + room.beds() + " beds occupied."));
      }
    }
    if (beds > 0) {
      int percent = (int) Math.floor(occupied * 100.0 / beds);
      String severity =
          percent >= policy.criticalPercent() ? "CRITICAL" : percent >= policy.warningPercent() ? "WARNING" : null;
      if (severity != null) {
        String key = "capacity:department";
        result.put(
            key,
            new Candidate(
                key, "DEPARTMENT_CAPACITY", severity, "Department", departmentId,
                "Department occupancy at " + percent + "%",
                occupied + " of " + beds + " beds occupied in active rooms. Warning at "
                    + policy.warningPercent() + "%, critical at " + policy.criticalPercent() + "%."));
      }
    }
    return result;
  }

  private Counts counts(Viewer viewer) {
    var args = new ArrayList<Object>();
    args.add(viewer.role());
    args.addAll(visibleArgs(viewer));
    return jdbc.queryForObject(
        "select count(*) filter (where r.user_id is null) as unread,"
            + " count(*) filter (where r.user_id is null and n.category = 'CAPACITY') as unread_alerts,"
            + " count(*) filter (where n.status = 'OPEN' and n.acknowledgement_due_at is not null"
            + " and n.acknowledged_at is null) as awaiting,"
            + " count(*) filter (where n.status = 'OPEN' and n.escalated_at is not null"
            + " and n.acknowledged_at is null and n.escalated_to_role = ?) as escalated"
            + " from notifications n left join notification_reads r on r.notification_id = n.id and r.user_id = ?"
            + VISIBLE,
        (rs, n) ->
            new Counts(rs.getLong("unread"), rs.getLong("unread_alerts"), rs.getLong("awaiting"), rs.getLong("escalated")),
        args.toArray());
  }

  private Notice find(Viewer viewer, long id) {
    if (viewer.departmentId() <= 0) throw ApiException.missing();
    var args = visibleArgs(viewer);
    args.add(id);
    var rows = jdbc.query(SELECT + VISIBLE + " and n.id = ?", (rs, n) -> notice(rs, viewer.role()), args.toArray());
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  /** Arguments for {@link #SELECT} joined with {@link #VISIBLE}: reader, department, recipient, now. */
  private static ArrayList<Object> visibleArgs(Viewer viewer) {
    var args = new ArrayList<Object>();
    args.add(viewer.userId());
    args.add(viewer.departmentId());
    args.add(viewer.userId());
    args.add(Timestamp.from(Instant.now()));
    return args;
  }

  private Viewer viewer() {
    var user = actor.user();
    return new Viewer(DepartmentContext.id(), user.getId(), user.getRole());
  }

  private static Notice notice(ResultSet rs, String role) throws SQLException {
    String status = rs.getString("status");
    Instant acknowledgedAt = instant(rs, "acknowledged_at");
    Instant escalatedAt = instant(rs, "escalated_at");
    Instant dueAt = instant(rs, "acknowledgement_due_at");
    String escalatedTo = rs.getString("escalated_to_role");
    return new Notice(
        rs.getLong("id"),
        rs.getLong("version"),
        rs.getString("category"),
        rs.getString("type"),
        rs.getString("severity"),
        status,
        rs.getString("title"),
        rs.getString("detail"),
        rs.getString("source_type"),
        nullableLong(rs, "source_id"),
        nullableLong(rs, "recipient_user_id") != null,
        rs.getBoolean("is_read"),
        instant(rs, "first_seen_at"),
        instant(rs, "last_seen_at"),
        instant(rs, "resolved_at"),
        instant(rs, "expires_at"),
        dueAt != null,
        dueAt,
        acknowledgedAt,
        nullableLong(rs, "acknowledged_by"),
        escalatedAt,
        escalatedTo,
        "OPEN".equals(status) && escalatedAt != null && acknowledgedAt == null && Objects.equals(escalatedTo, role));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static boolean needsAcknowledgement(String severity, NotificationPolicies.Policy policy) {
    return policy.escalationEnabled() && rank(severity) >= rank(policy.escalationMinSeverity());
  }

  private static int rank(String severity) {
    return switch (severity) {
      case "CRITICAL" -> 2;
      case "WARNING" -> 1;
      default -> 0;
    };
  }

  private static String option(String value, Set<String> allowed, String message) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.strip().toUpperCase(Locale.ROOT);
    if (!allowed.contains(normalized)) throw new ApiException(400, "VALIDATION_ERROR", message);
    return normalized;
  }
}
