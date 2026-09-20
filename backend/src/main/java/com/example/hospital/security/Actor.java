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
        .filter(u -> u.enabled)
        .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    var scope = DepartmentContext.current();
    if (scope == null) return user;
    // Never overwrite the managed account's global role with a workspace role.
    var view = new AppUser();
    view.id = user.id; view.version = user.version; view.username = user.username;
    view.role = scope.role(); view.doctorId = scope.doctorId();
    view.patientId = user.patientId; view.email = user.email;
    view.emailVerified = user.emailVerified; view.requestedRole = user.requestedRole;
    view.enabled = user.enabled; view.lastLoginAt = user.lastLoginAt;
    view.createdAt = user.createdAt; view.updatedAt = user.updatedAt;
    return view;
  }

  public boolean doctor() {
    return user().role.equals("DOCTOR");
  }

  public void staff() {
    if (!java.util.Set.of("ADMIN", "MEDICAL_STAFF").contains(user().role))
      throw new AccessDeniedException("Staff required");
  }

  public void admin() {
    if (!user().role.equals("ADMIN")) throw new AccessDeniedException("Admin required");
  }
}
