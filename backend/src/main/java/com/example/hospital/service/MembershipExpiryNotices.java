package com.example.hospital.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Warns members before a time-limited department membership ends (#347). Each membership is
 * claimed once with an atomic update, so several backend instances never send the notice twice.
 */
@Component
public class MembershipExpiryNotices {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final Duration notice;
  private final boolean enabled;

  public MembershipExpiryNotices(
      JdbcTemplate jdbc,
      TransactionTemplate transactions,
      @Value("${app.memberships.expiry-notice:3d}") Duration notice,
      @Value("${app.memberships.expiry-sweep-enabled:true}") boolean enabled) {
    if (notice.isNegative() || notice.isZero())
      throw new IllegalStateException("MEMBERSHIP_EXPIRY_NOTICE must be positive.");
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.notice = notice;
    this.enabled = enabled;
  }

  @Scheduled(
      initialDelayString = "${app.memberships.expiry-sweep-initial-delay-ms:45000}",
      fixedDelayString = "${app.memberships.expiry-sweep-interval-ms:900000}")
  public void sweep() {
    if (enabled) notifyExpiring();
  }

  /** Creates one personal notice per membership that entered the notice period. */
  public int notifyExpiring() {
    Integer sent =
        transactions.execute(
            status -> {
              Instant now = Instant.now();
              var claimed =
                  jdbc.queryForList(
                      """
                      update department_memberships set expiry_notified_at = now()
                       where expires_at is not null and expiry_notified_at is null
                         and expires_at > now() and expires_at <= ?
                      returning department_id, user_id, expires_at
                      """,
                      Timestamp.from(now.plus(notice)));
              for (var row : claimed) {
                long departmentId = ((Number) row.get("department_id")).longValue();
                Instant endsAt = ((Timestamp) row.get("expires_at")).toInstant();
                String department =
                    jdbc.queryForObject("select name from departments where id = ?", String.class, departmentId);
                jdbc.update(
                    "insert into notifications(department_id, recipient_user_id, category, type, severity,"
                        + " source_type, source_id, title, detail, status, first_seen_at, last_seen_at, expires_at)"
                        + " values (?, ?, 'ACTIVITY', 'MEMBERSHIP_EXPIRING', 'WARNING', 'Department', ?, ?, ?,"
                        + " 'INFO', ?, ?, ?)",
                    departmentId,
                    ((Number) row.get("user_id")).longValue(),
                    departmentId,
                    "Your department access ends soon",
                    "Access to "
                        + department
                        + " ends at "
                        + DateTimeFormatter.ISO_INSTANT.format(endsAt.truncatedTo(ChronoUnit.MINUTES))
                        + ". Ask a department administrator to extend it.",
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(endsAt));
              }
              return claimed.size();
            });
    return sent == null ? 0 : sent;
  }
}
