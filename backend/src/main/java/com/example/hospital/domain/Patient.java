package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "patients")
public class Patient extends DepartmentEntity {
  private String patientIdentifier;
  private String firstName;
  private String lastName;
  private java.time.LocalDate dateOfBirth;
  private String address;
  private String phoneNumber;

  public String getPatientIdentifier() {
    return patientIdentifier;
  }

  public void setPatientIdentifier(String patientIdentifier) {
    this.patientIdentifier = patientIdentifier;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public java.time.LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(java.time.LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }
}
