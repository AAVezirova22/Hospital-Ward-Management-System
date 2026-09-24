package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revocable iCalendar feed of expected discharge dates for one account and department (#392).
 * Calendar apps cannot send the session cookie, so the feed URL carries a random token; only its
 * hash is stored. Events name the room and admission number, never the patient, because feeds are
 * synchronised to devices outside the application. Access is re-checked on every fetch.
 */
@Service
public class CalendarFeedService {
  private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;
  private final String publicUrl;
  private final SecureRandom random = new SecureRandom();

  public CalendarFeedService(
      JdbcTemplate jdbc, Actor actor, AuditService audit, @Value("${app.public-url:}") String publicUrl) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
    this.publicUrl = publicUrl == null ? "" : publicUrl.strip().replaceAll("/+$", "");
  }

  /** Creates a new feed for the active department, revoking any previous one. The URL is shown once. */
  @Transactional
  public Map<String, Object> create() {
    long userId = actor.user().getId();
    long departmentId = DepartmentContext.id();
    if (departmentId <= 0) throw new ApiException(400, "DEPARTMENT_REQUIRED", "Open a department first.");
    jdbc.update(
        "update calendar_feeds set revoked_at = now() where user_id = ? and department_id = ? and revoked_at is null",
        userId, departmentId);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    jdbc.update(
        "insert into calendar_feeds(user_id, department_id, token_hash) values (?,?,?)",
        userId, departmentId, sha256(token));
    audit.log("CALENDAR_FEED_CREATED", "Department", departmentId, "UI");
    String path = "/api/v1/calendar/feeds/" + token + ".ics";
    var result = new LinkedHashMap<String, Object>();
    result.put("url", publicUrl.isEmpty() ? path : publicUrl + path);
    result.put("departmentId", departmentId);
    result.put("createdAt", Instant.now());
    return result;
  }

  @Transactional
  public void revoke() {
    long departmentId = DepartmentContext.id();
    int revoked = jdbc.update(
        "update calendar_feeds set revoked_at = now() where user_id = ? and department_id = ? and revoked_at is null",
        actor.user().getId(), departmentId);
    if (revoked == 0) throw new ApiException(404, "NOT_FOUND", "No active calendar feed for this department.");
    audit.log("CALENDAR_FEED_REVOKED", "Department", departmentId, "UI");
  }

  /** Renders the feed, or empty when the token is unknown, revoked or its owner lost access. */
  @Transactional
  public Optional<String> render(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{20,100}")) return Optional.empty();
    var feeds = jdbc.queryForList(
        """
        select f.id, f.department_id, d.name as department, m.role, m.doctor_id
          from calendar_feeds f
          join app_users u on u.id = f.user_id and u.enabled = true
          join departments d on d.id = f.department_id
          join department_memberships m on m.user_id = f.user_id and m.department_id = f.department_id
         where f.token_hash = ? and f.revoked_at is null
           and (m.expires_at is null or m.expires_at > now())
        """,
        sha256(token));
    if (feeds.isEmpty()) return Optional.empty();
    var feed = feeds.getFirst();
    long departmentId = ((Number) feed.get("department_id")).longValue();
    boolean doctor = "DOCTOR".equals(feed.get("role"));
    Object doctorId = feed.get("doctor_id");
    jdbc.update("update calendar_feeds set last_used_at = now() where id = ?", feed.get("id"));
    var rows = jdbc.queryForList(
        """
        select a.id, a.expected_discharge_date, r.room_number
          from admissions a
          left join room_assignments ra on ra.admission_id = a.id and ra.released_at is null
          left join rooms r on r.id = ra.room_id
         where a.department_id = ? and a.status = 'ACTIVE' and a.expected_discharge_date is not null
           and (? = false or a.attending_doctor_id = ?)
         order by a.expected_discharge_date, a.id
        """,
        departmentId, doctor, doctorId == null ? -1L : ((Number) doctorId).longValue());
    String stamp = STAMP.format(Instant.now());
    var ics = new StringBuilder()
        .append("BEGIN:VCALENDAR\r\n")
        .append("VERSION:2.0\r\n")
        .append("PRODID:-//Medcore//Expected discharges//EN\r\n")
        .append("CALSCALE:GREGORIAN\r\n")
        .append("X-WR-CALNAME:").append(text("Expected discharges - " + feed.get("department"))).append("\r\n");
    for (var row : rows) {
      LocalDate day = ((Date) row.get("expected_discharge_date")).toLocalDate();
      Object room = row.get("room_number");
      ics.append("BEGIN:VEVENT\r\n")
          .append("UID:admission-").append(row.get("id")).append("-expected-discharge@medcore\r\n")
          .append("DTSTAMP:").append(stamp).append("\r\n")
          .append("DTSTART;VALUE=DATE:").append(DAY.format(day)).append("\r\n")
          .append("DTEND;VALUE=DATE:").append(DAY.format(day.plusDays(1))).append("\r\n")
          .append("SUMMARY:").append(text("Expected discharge" + (room == null ? "" : " - Room " + room))).append("\r\n")
          .append("DESCRIPTION:").append(text("Admission #" + row.get("id"))).append("\r\n")
          .append("TRANSP:TRANSPARENT\r\n")
          .append("END:VEVENT\r\n");
    }
    return Optional.of(ics.append("END:VCALENDAR\r\n").toString());
  }

  /** RFC 5545 text escaping; values are kept short, so no line folding is needed. */
  private static String text(String value) {
    String escaped = value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n").replace("\r", "");
    return escaped.length() > 60 ? escaped.substring(0, 60) : escaped;
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
