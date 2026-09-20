package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms")
public class Room extends DepartmentEntity {
  private String roomNumber;
  private int bedCount;
  private boolean active = true;

  public String getRoomNumber() { return roomNumber; }
  public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
  public int getBedCount() { return bedCount; }
  public void setBedCount(int bedCount) { this.bedCount = bedCount; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
