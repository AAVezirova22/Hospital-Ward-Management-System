package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_sessions")
public class AiSession extends DepartmentEntity {
  private String sessionKey;
  private Long userId;
  private Long selectedPatientId;

  public String getSessionKey() { return sessionKey; }
  public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Long getSelectedPatientId() { return selectedPatientId; }
  public void setSelectedPatientId(Long selectedPatientId) { this.selectedPatientId = selectedPatientId; }
}
