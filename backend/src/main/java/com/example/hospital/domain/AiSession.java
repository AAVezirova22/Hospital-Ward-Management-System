package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_sessions")
public class AiSession extends BaseEntity {
  public String sessionKey;
  public Long userId;
  public Long selectedPatientId;
}
