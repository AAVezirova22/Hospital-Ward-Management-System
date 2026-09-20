package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_pending_actions")
public class AiPendingAction extends DepartmentEntity {
  private Long userId;
  private String actionType;

  @Column(columnDefinition = "text")
  private String payload;

  private String status = "PENDING";
  private java.time.Instant expiresAt;
  private java.time.Instant confirmedAt;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getActionType() {
    return actionType;
  }

  public void setActionType(String actionType) {
    this.actionType = actionType;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public java.time.Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(java.time.Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public java.time.Instant getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(java.time.Instant confirmedAt) {
    this.confirmedAt = confirmedAt;
  }
}
