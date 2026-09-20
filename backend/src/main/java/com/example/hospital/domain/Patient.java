package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "patients")
public class Patient extends DepartmentEntity {
  public String patientIdentifier;
  public String firstName;
  public String lastName;
  public java.time.LocalDate dateOfBirth;
  public String address;
  public String phoneNumber;
}
