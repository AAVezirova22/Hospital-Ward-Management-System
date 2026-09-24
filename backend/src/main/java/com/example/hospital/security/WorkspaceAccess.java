package com.example.hospital.security;

import com.example.hospital.api.ApiException;
import com.example.hospital.domain.AppUser;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceAccess {
  /** SQL condition for a department membership that still grants access (alias {@code m}). */
  public static final String ACTIVE_MEMBERSHIP = "(m.expires_at is null or m.expires_at > now())";

  private record Membership(DepartmentContext.Scope scope, Instant expiresAt) {
    boolean active(Instant now) {
      return expiresAt == null || expiresAt.isAfter(now);
    }
  }

  private final JdbcTemplate jdbc;
  public WorkspaceAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public DepartmentContext.Scope resolve(AppUser user, String requested) {
    if ("PATIENT".equals(user.getRole())) {
      var ids = jdbc.queryForList("select department_id from patients where id = ?", Long.class, user.getPatientId());
      return new DepartmentContext.Scope(ids.isEmpty() ? -1L : ids.getFirst(), "PATIENT", null);
    }
    Long id = null;
    if (requested != null) {
      try { id = Long.valueOf(requested); }
      catch (NumberFormatException e) { throw new ApiException(400, "INVALID_DEPARTMENT", "Choose a valid department."); }
    }
    var memberships = jdbc.query("select department_id, role, doctor_id, expires_at from department_memberships where user_id = ?"
        + (id == null ? " order by department_id" : " and department_id = ?"),
        (rs, n) -> {
          Timestamp expires = rs.getTimestamp(4);
          return new Membership(
              new DepartmentContext.Scope(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class)),
              expires == null ? null : expires.toInstant());
        },
        id == null ? new Object[]{user.getId()} : new Object[]{user.getId(), id});
    var now = Instant.now();
    var active = memberships.stream().filter(m -> m.active(now)).map(Membership::scope).toList();
    if (active.isEmpty() && id != null) {
      if (!memberships.isEmpty())
        throw new ApiException(403, "MEMBERSHIP_EXPIRED",
            "Your access to this department has ended. Ask a department administrator to extend it.");
      throw new ApiException(403, "DEPARTMENT_ACCESS_DENIED", "Join this department before opening it.");
    }
    return active.isEmpty() ? new DepartmentContext.Scope(-1L, "MEDICAL_STAFF", null) : active.getFirst();
  }
}
