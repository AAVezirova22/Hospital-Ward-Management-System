package com.example.hospital.security;

import com.example.hospital.domain.AppUser;
import com.example.hospital.repository.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
public class Actor {
  private final AppUserRepository users;
  private final JdbcTemplate jdbc;

  public Actor(AppUserRepository users, JdbcTemplate jdbc) {
    this.users = users;
    this.jdbc = jdbc;
  }

  public AppUser user() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null) throw new AccessDeniedException("Unauthenticated");
    var user = users
        .findByUsername(a.getName())
        .filter(u -> u.isEnabled())
        .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    var scope = DepartmentContext.current();
    if (scope == null) return user;
    // Never overwrite the managed account's global role with a workspace role.
    var view = new AppUser();
    view.setId(user.getId()); view.setVersion(user.getVersion()); view.setUsername(user.getUsername());
    view.setRole(scope.role()); view.setDoctorId(scope.doctorId());
    view.setPatientId(user.getPatientId()); view.setEmail(user.getEmail());
    view.setEmailVerified(user.isEmailVerified()); view.setRequestedRole(user.getRequestedRole());
    view.setEnabled(user.isEnabled()); view.setLastLoginAt(user.getLastLoginAt());
    view.setCreatedAt(user.getCreatedAt()); view.setUpdatedAt(user.getUpdatedAt());
    view.setAccountRole(user.getRole());
    view.setDepartmentRole(scope.role());
    return view;
  }

  public boolean doctor() {
    return user().getRole().equals("DOCTOR");
  }

  public void staff() {
    if (!java.util.Set.of("ADMIN", "MEDICAL_STAFF").contains(user().getRole()))
      throw new AccessDeniedException("Staff required");
  }

  /** A small department membership capability for patient document import and its patient form save. */
  public boolean canImportPatient() {
    var current = user();
    if (java.util.Set.of("ADMIN", "MEDICAL_STAFF").contains(current.getRole())) return true;
    return current.getRole().equals("DOCTOR") && current.getDoctorId() != null
        && Boolean.TRUE.equals(jdbc.queryForObject(
            "select count(*) > 0 from department_memberships where department_id=? and user_id=? and role='DOCTOR' and doctor_id=? and patient_import_enabled=true",
            Boolean.class, DepartmentContext.id(), current.getId(), current.getDoctorId()));
  }

  public void requirePatientImport() {
    if (!canImportPatient()) throw new AccessDeniedException("Patient document import permission required");
  }

  public void admin() {
    if (!user().getRole().equals("ADMIN")) throw new AccessDeniedException("Admin required");
  }
}
