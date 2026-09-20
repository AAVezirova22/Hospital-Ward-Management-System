package com.example.hospital.repository;

import com.example.hospital.domain.AppUser;
import org.springframework.data.jpa.repository.*;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  java.util.Optional<AppUser> findByUsername(String username);

  long countByRoleAndEnabledTrue(String role);
}
