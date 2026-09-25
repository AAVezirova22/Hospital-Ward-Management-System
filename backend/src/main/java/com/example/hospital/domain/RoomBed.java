package com.example.hospital.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_beds")
public class RoomBed extends DepartmentEntity {
  private Long roomId;
  @Column(nullable = false, length = 64)
  private String identifier;

  public Long getRoomId() { return roomId; }
  public void setRoomId(Long roomId) { this.roomId = roomId; }
  public String getIdentifier() { return identifier; }
  public void setIdentifier(String identifier) { this.identifier = identifier; }
}
