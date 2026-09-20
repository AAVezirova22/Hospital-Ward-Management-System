package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_pending_actions")
public class AiPendingAction extends BaseEntity {
  public Long userId;
  public String actionType;

  @Column(columnDefinition = "text")
  public String payload;

  public String status = "PENDING";
  public java.time.Instant expiresAt;
  public java.time.Instant confirmedAt;
}
