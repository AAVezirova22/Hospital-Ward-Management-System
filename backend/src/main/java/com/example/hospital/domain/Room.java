package com.example.hospital.domain;

import jakarta.persistence.*;
import java.util.Set;
import java.util.TreeSet;

@Entity
@Table(name = "rooms")
public class Room extends DepartmentEntity {
  private String roomNumber;
  private int bedCount;
  private boolean active = true;
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "room_capabilities",
      joinColumns = @JoinColumn(name = "room_id"),
      uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "capability"}))
  @Column(name = "capability", nullable = false, length = 64)
  private Set<String> capabilities = new TreeSet<>();

  public String getRoomNumber() { return roomNumber; }
  public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
  public int getBedCount() { return bedCount; }
  public void setBedCount(int bedCount) { this.bedCount = bedCount; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Set<String> getCapabilities() { return capabilities; }
  public void setCapabilities(Set<String> capabilities) {
    this.capabilities = capabilities == null ? new TreeSet<>() : new TreeSet<>(capabilities);
  }
}
