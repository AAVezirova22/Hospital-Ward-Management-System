package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms")
public class Room extends DepartmentEntity {
  public String roomNumber;
  public int bedCount;
  public boolean active = true;
}
