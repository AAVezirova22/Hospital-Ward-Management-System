package com.example.hospital.service;

import com.example.hospital.api.*;
import com.example.hospital.api.UserInput;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
  private final AppUserRepository users;
  private final DoctorRepository doctors;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final WorkspaceService workspaces;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public UserService(
      AppUserRepository u,
      DoctorRepository d,
      WorkflowLockRepository l,
      Actor a,
      PasswordEncoder e,
      AuditService au, WorkspaceService workspaces, org.springframework.jdbc.core.JdbcTemplate jdbc) {
    users = u;
    doctors = d;
    lock = l;
    actor = a;
    encoder = e;
    audit = au;
    this.workspaces = workspaces;
    this.jdbc = jdbc;
  }

  public List<AppUser> list() {
    actor.admin();
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    var ids = new LinkedHashSet<Long>();
    ids.addAll(jdbc.queryForList("select user_id from department_memberships where department_id=?", Long.class, departmentId));
    ids.addAll(jdbc.queryForList(
        "select u.id from app_users u join patients p on p.id=u.patient_id where p.department_id=?",
        Long.class, departmentId));
    if (ids.isEmpty()) return List.of();
    return users.findAllById(ids).stream().map(this::scoped).toList();
  }

  private AppUser scoped(AppUser user) {
    var rows =
        jdbc.queryForList(
            "select role, doctor_id from department_memberships where department_id=? and user_id=?",
            com.example.hospital.security.DepartmentContext.id(),
            user.id);
    if (rows.isEmpty()) return user;
    var row = rows.getFirst();
    var view = new AppUser();
    view.id = user.id;
    view.version = user.version;
    view.username = user.username;
    view.role = String.valueOf(row.get("role"));
    view.accountRole = user.role;
    view.departmentRole = view.role;
    view.doctorId = row.get("doctor_id") == null ? null : ((Number) row.get("doctor_id")).longValue();
    view.patientId = user.patientId;
    view.email = user.email;
    view.emailVerified = user.emailVerified;
    view.requestedRole = user.requestedRole;
    view.enabled = user.enabled;
    view.lastLoginAt = user.lastLoginAt;
    view.createdAt = user.createdAt;
    view.updatedAt = user.updatedAt;
    return view;
  }

  private boolean visible(AppUser user) {
    return workspaces.member(user.id) || (user.patientId != null && Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from patients where id=? and department_id=?", Boolean.class,
        user.patientId, com.example.hospital.security.DepartmentContext.id())));
  }

  @Transactional
  public AppUser save(Long id, UserInput in) {
    lock.acquire();
    actor.admin();
    var u = id == null ? new AppUser() : users.findById(id).orElseThrow(ApiException::missing);
    if (id != null && !visible(u)) throw ApiException.missing();
    if (id != null && Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from department_memberships where user_id=? and department_id<>?", Boolean.class,
        id, com.example.hospital.security.DepartmentContext.id())))
      throw ApiException.conflict("SHARED_ACCOUNT", "This account belongs to multiple departments. Its account settings cannot be changed from a single department.");
    if (id != null) HospitalService.version(u, in.version());
    if (id != null && id.equals(actor.user().id) && (!in.enabled() || !in.role().equals("ADMIN")))
      throw ApiException.conflict(
          "SELF_LOCKOUT", "You cannot remove your own administrator access.");
    if (id != null
        && u.role.equals("ADMIN")
        && u.enabled
        && (!in.enabled() || !in.role().equals("ADMIN"))
        && users.countByRoleAndEnabledTrue("ADMIN") <= 1)
      throw ApiException.conflict("LAST_ADMIN", "Keep at least one enabled administrator.");
    if (in.role().equals("DOCTOR")
        && (in.doctorId() == null
            || doctors.findById(in.doctorId()).filter(d -> d.active).isEmpty()))
      throw new ApiException(400, "DOCTOR_REQUIRED", "Link an active doctor account.");
    if ("DOCTOR".equals(in.role()) && "DOCTOR".equals(u.requestedRole) && !u.emailVerified)
      throw new ApiException(400, "EMAIL_UNVERIFIED", "The applicant must confirm their email first.");
    if ("PATIENT".equals(in.role()) && u.patientId == null)
      throw new ApiException(400, "PATIENT_REQUIRED", "Patient accounts are created through registration.");
    if (u.email != null && !u.emailVerified && in.enabled())
      throw new ApiException(400, "EMAIL_UNVERIFIED", "Confirm the account email before enabling access.");
    if (id != null && !u.username.equals(in.username()))
      throw new ApiException(400, "USERNAME_IMMUTABLE", "Usernames cannot be changed.");
    if (id == null || in.password() != null && !in.password().isBlank()) {
      if (in.password() == null
          || in.password().length() < 12
          || in.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        throw new ApiException(
            400,
            "PASSWORD_LENGTH",
            "Use a password of at least 12 characters and at most 72 UTF-8 bytes.");
      u.passwordHash = encoder.encode(in.password());
      u.sessionStamp = com.example.hospital.security.SessionStamps.next();
    }
    u.username = in.username();
    u.role = in.role();
    u.enabled = in.enabled();
    u.doctorId = in.role().equals("DOCTOR") ? in.doctorId() : null;
    users.saveAndFlush(u);
    if (!"PATIENT".equals(u.role)) workspaces.enroll(u.id, u.role, u.doctorId);
    audit.log("USER_SAVED", "User", u.id, "UI");
    return u;
  }
}
