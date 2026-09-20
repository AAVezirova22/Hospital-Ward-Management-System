package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "doctors")
public class Doctor extends BaseEntity {
  public String doctorIdentifier;
  public String firstName;
  public String lastName;
  public String specialty;
  public boolean active = true;
}
