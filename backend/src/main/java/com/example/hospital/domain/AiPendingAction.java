package com.example.hospital.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_pending_actions")
public class AiPendingAction extends DepartmentEntity {
  private Long userId;
  private String actionType;

  @Column(columnDefinition = "text")
  private String payload;

  private String status = "PENDING";
  private Instant expiresAt;
  private Instant confirmedAt;

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getActionType() { return actionType; }
  public void setActionType(String actionType) { this.actionType = actionType; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getConfirmedAt() { return confirmedAt; }
  public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
