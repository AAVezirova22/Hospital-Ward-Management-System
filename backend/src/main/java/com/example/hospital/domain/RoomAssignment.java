package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "room_assignments")
public class RoomAssignment extends DepartmentEntity {
  private Long admissionId;
  private Long roomId;
  private java.time.Instant assignedAt;
  private java.time.Instant releasedAt;
  private String reason;
  private Long createdBy;

  public Long getAdmissionId() {
    return admissionId;
  }

  public void setAdmissionId(Long admissionId) {
    this.admissionId = admissionId;
  }

  public Long getRoomId() {
    return roomId;
  }

  public void setRoomId(Long roomId) {
    this.roomId = roomId;
  }

  public java.time.Instant getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(java.time.Instant assignedAt) {
    this.assignedAt = assignedAt;
  }

  public java.time.Instant getReleasedAt() {
    return releasedAt;
  }

  public void setReleasedAt(java.time.Instant releasedAt) {
    this.releasedAt = releasedAt;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long createdBy) {
    this.createdBy = createdBy;
  }
}
