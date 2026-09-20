package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {
  public Long userId;
  public String eventType;
  public String entityType;
  public Long entityId;
  public String source;
  public java.time.Instant timestamp;
  public String metadata;
}
