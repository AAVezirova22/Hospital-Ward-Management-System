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
    return users
        .findByUsername(a.getName())
        .filter(u -> u.enabled)
        .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
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
