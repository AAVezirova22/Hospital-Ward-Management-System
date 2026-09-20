package com.example.hospital.service;

import com.example.hospital.api.*;
import com.example.hospital.api.Inputs.UserInput;
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

  public UserService(
      AppUserRepository u,
      DoctorRepository d,
      WorkflowLockRepository l,
      Actor a,
      PasswordEncoder e,
      AuditService au) {
    users = u;
    doctors = d;
    lock = l;
    actor = a;
    encoder = e;
    audit = au;
  }

  public List<AppUser> list() {
    actor.admin();
    return users.findAll();
  }

  @Transactional
  public AppUser save(Long id, UserInput in) {
    lock.acquire();
    actor.admin();
    var u = id == null ? new AppUser() : users.findById(id).orElseThrow(ApiException::missing);
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
    }
    u.username = in.username();
    u.role = in.role();
    u.enabled = in.enabled();
    u.doctorId = in.role().equals("DOCTOR") ? in.doctorId() : null;
    users.saveAndFlush(u);
    audit.log("USER_SAVED", "User", u.id, "UI");
    return u;
  }
}
