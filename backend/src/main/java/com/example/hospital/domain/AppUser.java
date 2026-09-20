package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {
  public String username;
  @com.fasterxml.jackson.annotation.JsonIgnore public String passwordHash;
  public String role;
  public boolean enabled = true;
  public Long doctorId;
  public Long patientId;
  public String email;
  public boolean emailVerified;
  public String requestedRole;
  public java.time.Instant lastLoginAt;
}
