package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_interactions")
public class AiInteraction extends DepartmentEntity {
  public Long userId;
  public String sessionId;
  public String requestType;
  public String modelIdentifier;
  public String toolNames;
  public String status;
  public java.time.Instant startedAt;
  public java.time.Instant completedAt;
  public Long latencyMs;
}
