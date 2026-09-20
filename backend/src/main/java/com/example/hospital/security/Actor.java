package com.example.hospital.security;

import com.example.hospital.domain.AppUser;
import com.example.hospital.repository.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Actor {
  private final AppUserRepository users;

  public Actor(AppUserRepository users) {
    this.users = users;
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

  public void admin() {
    if (!user().getRole().equals("ADMIN")) throw new AccessDeniedException("Admin required");
  }
}
