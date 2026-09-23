package com.example.hospital.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Durable, database-backed queue for confirmation-email deliveries. */
@Repository
public class EmailOutboxStore {
  public static final int MAX_ATTEMPTS = 8;
  private final JdbcTemplate jdbc;

  public EmailOutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public void enqueue(long userId, String recipient, String firstName, String token,
      boolean doctor, int expiryMinutes, Instant expiresAt) {
    jdbc.update("""
        insert into email_outbox(user_id, recipient, first_name, confirmation_token, doctor,
            expiry_minutes, expires_at)
        values (?, ?, ?, ?, ?, ?, ?)
        """, userId, recipient, firstName, token, doctor, expiryMinutes, Timestamp.from(expiresAt));
  }

  @Transactional
  public void cancelOpenForUser(long userId) {
    jdbc.update("""
        update email_outbox
        set status='CANCELLED', recipient=null, first_name=null, confirmation_token=null,
            lease_until=null, last_error=null
        where user_id=? and status in ('PENDING', 'SENDING')
        """, userId);
  }

  /** Claims and commits a bounded batch before any network calls are made. */
  @Transactional
  public List<Delivery> claimDue(int batchSize) {
    jdbc.update("""
        update email_outbox
        set status='FAILED', recipient=null, first_name=null, confirmation_token=null,
            lease_until=null, last_error='MAX_ATTEMPTS'
        where attempts >= ? and (status='PENDING'
            or (status='SENDING' and lease_until <= now()))
        """, MAX_ATTEMPTS);

    return jdbc.query("""
        with due as (
          select id
          from email_outbox
          where ((status='PENDING' and next_attempt_at <= now())
              or (status='SENDING' and lease_until <= now()))
            and expires_at > now() and attempts < ?
          order by next_attempt_at, id
          for update skip locked
          limit ?
        )
        update email_outbox o
        set status='SENDING', attempts=o.attempts + 1,
            lease_until=now() + interval '5 minutes'
        from due
        where o.id=due.id
        returning o.id, o.recipient, o.first_name, o.confirmation_token, o.doctor,
            o.expiry_minutes, o.expires_at, o.attempts
        """, (rs, row) -> mapDelivery(rs), MAX_ATTEMPTS, batchSize);
  }

  @Transactional
  public void markSent(long id) {
    jdbc.update("""
        update email_outbox
        set status='SENT', recipient=null, first_name=null, confirmation_token=null,
            lease_until=null, sent_at=now(), last_error=null
        where id=? and status='SENDING'
        """, id);
  }

  @Transactional
  public void retryOrFinish(Delivery delivery, String safeError) {
    long delaySeconds = Math.min(300L, 5L << Math.min(delivery.attempts() - 1, 6));
    jdbc.update("""
        update email_outbox
        set status=case
              when expires_at <= now() then 'EXPIRED'
              when attempts >= ? then 'FAILED'
              else 'PENDING'
            end,
            recipient=case when expires_at <= now() or attempts >= ? then null else recipient end,
            first_name=case when expires_at <= now() or attempts >= ? then null else first_name end,
            confirmation_token=case when expires_at <= now() or attempts >= ? then null else confirmation_token end,
            next_attempt_at=case when expires_at > now() and attempts < ?
              then now() + (? * interval '1 second') else next_attempt_at end,
            lease_until=null,
            last_error=case when expires_at <= now() then 'CONFIRMATION_EXPIRED'
              when attempts >= ? then 'MAX_ATTEMPTS' else ? end
        where id=? and status='SENDING'
        """, MAX_ATTEMPTS, MAX_ATTEMPTS, MAX_ATTEMPTS, MAX_ATTEMPTS,
        MAX_ATTEMPTS, delaySeconds, MAX_ATTEMPTS, safeError, delivery.id());
  }

  @Transactional
  public void cleanup() {
    jdbc.update("""
        update email_outbox
        set status='EXPIRED', recipient=null, first_name=null, confirmation_token=null,
            lease_until=null, last_error='CONFIRMATION_EXPIRED'
        where status in ('PENDING', 'SENDING') and expires_at <= now()
        """);
    jdbc.update("""
        delete from email_outbox
        where status in ('SENT', 'FAILED', 'CANCELLED', 'EXPIRED')
          and created_at < now() - interval '30 days'
        """);
  }

  private static Delivery mapDelivery(ResultSet rs) throws SQLException {
    return new Delivery(rs.getLong("id"), rs.getString("recipient"), rs.getString("first_name"),
        rs.getString("confirmation_token"), rs.getBoolean("doctor"), rs.getInt("expiry_minutes"),
        rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempts"));
  }

  public record Delivery(long id, String recipient, String firstName, String token,
      boolean doctor, int expiryMinutes, Instant expiresAt, int attempts) { }
}
