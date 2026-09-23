package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_sessions")
public class AiSession extends DepartmentEntity {
  private String sessionKey;
  private Long userId;
  private Long selectedPatientId;
  @Column(columnDefinition = "text") private String conversationContext;
  private java.time.Instant conversationExpiresAt;

  public String getSessionKey() { return sessionKey; }
  public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Long getSelectedPatientId() { return selectedPatientId; }
  public void setSelectedPatientId(Long selectedPatientId) { this.selectedPatientId = selectedPatientId; }
  public String getConversationContext() { return conversationContext; }
  public void setConversationContext(String conversationContext) { this.conversationContext = conversationContext; }
  public java.time.Instant getConversationExpiresAt() { return conversationExpiresAt; }
  public void setConversationExpiresAt(java.time.Instant conversationExpiresAt) { this.conversationExpiresAt = conversationExpiresAt; }
}
