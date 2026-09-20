package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "doctors")
public class Doctor extends DepartmentEntity {
  private String doctorIdentifier;
  private String firstName;
  private String lastName;
  private String specialty;
  private boolean active = true;

  public String getDoctorIdentifier() { return doctorIdentifier; }
  public void setDoctorIdentifier(String doctorIdentifier) { this.doctorIdentifier = doctorIdentifier; }
  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }
  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }
  public String getSpecialty() { return specialty; }
  public void setSpecialty(String specialty) { this.specialty = specialty; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
