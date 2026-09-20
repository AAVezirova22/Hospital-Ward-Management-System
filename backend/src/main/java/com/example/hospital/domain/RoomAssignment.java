package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "room_assignments")
public class RoomAssignment extends DepartmentEntity {
  public Long admissionId;
  public Long roomId;
  public java.time.Instant assignedAt;
  public java.time.Instant releasedAt;
  public String reason;
  public Long createdBy;
}
