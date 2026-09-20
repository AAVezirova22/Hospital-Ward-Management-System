package com.example.hospital.security;

import com.example.hospital.api.ApiException;
import com.example.hospital.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceAccess {
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
    var scopes = jdbc.query("select department_id, role, doctor_id from department_memberships where user_id = ?"
        + (id == null ? " order by department_id" : " and department_id = ?"),
        (rs, n) -> new DepartmentContext.Scope(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class)),
        id == null ? new Object[]{user.getId()} : new Object[]{user.getId(), id});
    if (scopes.isEmpty() && id != null)
      throw new ApiException(403, "DEPARTMENT_ACCESS_DENIED", "Join this department before opening it.");
    return scopes.isEmpty() ? new DepartmentContext.Scope(-1L, "MEDICAL_STAFF", null) : scopes.getFirst();
  }
}
