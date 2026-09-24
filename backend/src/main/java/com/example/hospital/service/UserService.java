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
            user.getId());
    if (rows.isEmpty()) return user;
    var row = rows.getFirst();
    var view = new AppUser();
    view.setId(user.getId());
    view.setVersion(user.getVersion());
    view.setUsername(user.getUsername());
    view.setRole(String.valueOf(row.get("role")));
    view.setAccountRole(user.getRole());
    view.setDepartmentRole(view.getRole());
    view.setDoctorId(row.get("doctor_id") == null ? null : ((Number) row.get("doctor_id")).longValue());
    view.setPatientId(user.getPatientId());
    view.setEmail(user.getEmail());
    view.setEmailVerified(user.isEmailVerified());
    view.setRequestedRole(user.getRequestedRole());
    view.setEnabled(user.isEnabled());
    view.setLastLoginAt(user.getLastLoginAt());
    view.setCreatedAt(user.getCreatedAt());
    view.setUpdatedAt(user.getUpdatedAt());
    return view;
  }

  private boolean visible(AppUser user) {
    return workspaces.member(user.getId()) || (user.getPatientId() != null && Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from patients where id=? and department_id=?", Boolean.class,
        user.getPatientId(), com.example.hospital.security.DepartmentContext.id())));
  }

  @Transactional
  public AppUser save(Long id, UserInput in) {
    lock.acquire();
    actor.admin();
    var u = id == null ? new AppUser() : users.findById(id).orElseThrow(ApiException::missing);
    String oldRole = id == null ? null : u.getRole();
    Long oldDoctorId = id == null ? null : u.getDoctorId();
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    var oldDepartmentMembership = id == null ? null : jdbc.query(
        "select role,doctor_id from department_memberships where department_id=? and user_id=?",
        rs -> rs.next() ? new Object[] {rs.getString(1), rs.getObject(2, Long.class)} : null,
        departmentId, id);
    if (id != null && !visible(u)) throw ApiException.missing();
    if (id != null && Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from department_memberships where user_id=? and department_id<>?", Boolean.class,
        id, com.example.hospital.security.DepartmentContext.id())))
      throw ApiException.conflict("SHARED_ACCOUNT", "This account belongs to multiple departments. Its account settings cannot be changed from a single department.");
    if (id != null) HospitalService.version(u, in.version());
    if (id != null && id.equals(actor.user().getId()) && (!in.enabled() || !in.role().equals("ADMIN")))
      throw ApiException.conflict(
          "SELF_LOCKOUT", "You cannot remove your own administrator access.");
    if (id != null
        && u.getRole().equals("ADMIN")
        && u.isEnabled()
        && (!in.enabled() || !in.role().equals("ADMIN"))
        && users.countByRoleAndEnabledTrue("ADMIN") <= 1)
      throw ApiException.conflict("LAST_ADMIN", "Keep at least one enabled administrator.");
    if (in.role().equals("DOCTOR")
        && (in.doctorId() == null
            || doctors.findById(in.doctorId()).filter(d -> d.isActive()).isEmpty()))
      throw new ApiException(400, "DOCTOR_REQUIRED", "Link an active doctor account.");
    if ("DOCTOR".equals(in.role()) && "DOCTOR".equals(u.getRequestedRole()) && !u.isEmailVerified())
      throw new ApiException(400, "EMAIL_UNVERIFIED", "The applicant must confirm their email first.");
    if ("PATIENT".equals(in.role()) && u.getPatientId() == null)
      throw new ApiException(400, "PATIENT_REQUIRED", "Patient accounts are created through registration.");
    if (u.getEmail() != null && !u.isEmailVerified() && in.enabled())
      throw new ApiException(400, "EMAIL_UNVERIFIED", "Confirm the account email before enabling access.");
    if (id != null && !u.getUsername().equals(in.username()))
      throw new ApiException(400, "USERNAME_IMMUTABLE", "Usernames cannot be changed.");
    if (id == null || in.password() != null && !in.password().isBlank()) {
      if (in.password() == null
          || in.password().length() < 12
          || in.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        throw new ApiException(
            400,
            "PASSWORD_LENGTH",
            "Use a password of at least 12 characters and at most 72 UTF-8 bytes.");
      u.setPasswordHash(encoder.encode(in.password()));
      u.setSessionStamp(com.example.hospital.security.SessionStamps.next());
    }
    u.setUsername(in.username());
    u.setRole(in.role());
    u.setEnabled(in.enabled());
    u.setDoctorId(in.role().equals("DOCTOR") ? in.doctorId() : null);
    users.saveAndFlush(u);
    if (!"PATIENT".equals(u.getRole())) workspaces.enroll(u.getId(), u.getRole(), u.getDoctorId());
    var newDepartmentMembership = jdbc.query(
        "select role,doctor_id from department_memberships where department_id=? and user_id=?",
        rs -> rs.next() ? new Object[] {rs.getString(1), rs.getObject(2, Long.class)} : null,
        departmentId, u.getId());
    var metadata = new LinkedHashMap<String, Object>();
    metadata.put("targetUserId", u.getId());
    metadata.put("workspaceType", "DEPARTMENT");
    metadata.put("workspaceId", departmentId);
    metadata.put("oldAccountRole", oldRole);
    metadata.put("newAccountRole", u.getRole());
    metadata.put("oldAccountDoctorId", oldDoctorId);
    metadata.put("newAccountDoctorId", u.getDoctorId());
    metadata.put("oldDepartmentRole", oldDepartmentMembership == null ? null : oldDepartmentMembership[0]);
    metadata.put("newDepartmentRole", newDepartmentMembership == null ? null : newDepartmentMembership[0]);
    metadata.put("oldDepartmentDoctorId", oldDepartmentMembership == null ? null : oldDepartmentMembership[1]);
    metadata.put("newDepartmentDoctorId", newDepartmentMembership == null ? null : newDepartmentMembership[1]);
    if (in.reason() != null && !in.reason().isBlank()) metadata.put("reason", in.reason().strip());
    audit.log("USER_SAVED", "User", u.getId(), "UI", metadata);
    return u;
  }
}
