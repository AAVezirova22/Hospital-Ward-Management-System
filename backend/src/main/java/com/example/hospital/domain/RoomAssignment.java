package com.example.hospital.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "room_assignments")
public class RoomAssignment extends DepartmentEntity {
  private Long admissionId;
  private Long roomId;
  private Long bedId;
  private String bedIdentifier;
  private Instant assignedAt;
  private Instant releasedAt;
  private String reason;
  private Long createdBy;

  public Long getAdmissionId() { return admissionId; }
  public void setAdmissionId(Long admissionId) { this.admissionId = admissionId; }
  public Long getRoomId() { return roomId; }
  public void setRoomId(Long roomId) { this.roomId = roomId; }
  public Long getBedId() { return bedId; }
  public void setBedId(Long bedId) { this.bedId = bedId; }
  public String getBedIdentifier() { return bedIdentifier; }
  public void setBedIdentifier(String bedIdentifier) { this.bedIdentifier = bedIdentifier; }
  public Instant getAssignedAt() { return assignedAt; }
  public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
  public Instant getReleasedAt() { return releasedAt; }
  public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Long getCreatedBy() { return createdBy; }
  public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
