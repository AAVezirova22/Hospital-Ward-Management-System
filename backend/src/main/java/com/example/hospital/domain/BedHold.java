package com.example.hospital.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bed_holds")
public class BedHold extends DepartmentEntity {
  private Long roomId;
  private int bedCount;
  private String reason;
  private Instant startsAt;
  private Instant endsAt;
  private Long createdBy;
  private Instant cancelledAt;
  private Long cancelledBy;

  public Long getRoomId() { return roomId; }
  public void setRoomId(Long roomId) { this.roomId = roomId; }
  public int getBedCount() { return bedCount; }
  public void setBedCount(int bedCount) { this.bedCount = bedCount; }
  @Column(length = 500, nullable = false)
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Instant getStartsAt() { return startsAt; }
  public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
  public Instant getEndsAt() { return endsAt; }
  public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
  public Long getCreatedBy() { return createdBy; }
  public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
  public Instant getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
  public Long getCancelledBy() { return cancelledBy; }
  public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }
}
