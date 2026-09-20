package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {
  private String username;
  @com.fasterxml.jackson.annotation.JsonIgnore private String passwordHash;
  private String role;
  private boolean enabled = true;
  private Long doctorId;
  private Long patientId;
  private String email;
  private boolean emailVerified;
  private String requestedRole;
  private java.time.Instant lastLoginAt;
  @com.fasterxml.jackson.annotation.JsonIgnore private String sessionStamp;
  @Transient private String accountRole;
  @Transient private String departmentRole;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Long getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(Long doctorId) {
    this.doctorId = doctorId;
  }

  public Long getPatientId() {
    return patientId;
  }

  public void setPatientId(Long patientId) {
    this.patientId = patientId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public String getRequestedRole() {
    return requestedRole;
  }

  public void setRequestedRole(String requestedRole) {
    this.requestedRole = requestedRole;
  }

  public java.time.Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(java.time.Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getSessionStamp() {
    return sessionStamp;
  }

  public void setSessionStamp(String sessionStamp) {
    this.sessionStamp = sessionStamp;
  }

  public String getAccountRole() {
    return accountRole;
  }

  public void setAccountRole(String accountRole) {
    this.accountRole = accountRole;
  }

  public String getDepartmentRole() {
    return departmentRole;
  }

  public void setDepartmentRole(String departmentRole) {
    this.departmentRole = departmentRole;
  }
}
