package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_interactions")
public class AiInteraction extends DepartmentEntity {
  private Long userId;
  private String sessionId;
  private String requestType;
  private String modelIdentifier;
  private String toolNames;
  private String status;
  private java.time.Instant startedAt;
  private java.time.Instant completedAt;
  private Long latencyMs;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getRequestType() {
    return requestType;
  }

  public void setRequestType(String requestType) {
    this.requestType = requestType;
  }

  public String getModelIdentifier() {
    return modelIdentifier;
  }

  public void setModelIdentifier(String modelIdentifier) {
    this.modelIdentifier = modelIdentifier;
  }

  public String getToolNames() {
    return toolNames;
  }

  public void setToolNames(String toolNames) {
    this.toolNames = toolNames;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public java.time.Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(java.time.Instant startedAt) {
    this.startedAt = startedAt;
  }

  public java.time.Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(java.time.Instant completedAt) {
    this.completedAt = completedAt;
  }

  public Long getLatencyMs() {
    return latencyMs;
  }

  public void setLatencyMs(Long latencyMs) {
    this.latencyMs = latencyMs;
  }
}
