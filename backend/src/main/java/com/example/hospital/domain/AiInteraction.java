package com.example.hospital.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_interactions")
public class AiInteraction extends DepartmentEntity {
  private Long userId;
  private String sessionId;
  private String requestType;
  private String modelIdentifier;
  private String toolNames;
  private String status;
  private Instant startedAt;
  private Instant completedAt;
  private Long latencyMs;
  private String retryToken;
  private String retryMessageHash;
  private int retryAttempt;

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
  public String getRequestType() { return requestType; }
  public void setRequestType(String requestType) { this.requestType = requestType; }
  public String getModelIdentifier() { return modelIdentifier; }
  public void setModelIdentifier(String modelIdentifier) { this.modelIdentifier = modelIdentifier; }
  public String getToolNames() { return toolNames; }
  public void setToolNames(String toolNames) { this.toolNames = toolNames; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
  public Long getLatencyMs() { return latencyMs; }
  public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
  public String getRetryToken() { return retryToken; }
  public void setRetryToken(String retryToken) { this.retryToken = retryToken; }
  public String getRetryMessageHash() { return retryMessageHash; }
  public void setRetryMessageHash(String retryMessageHash) { this.retryMessageHash = retryMessageHash; }
  public int getRetryAttempt() { return retryAttempt; }
  public void setRetryAttempt(int retryAttempt) { this.retryAttempt = retryAttempt; }
}
