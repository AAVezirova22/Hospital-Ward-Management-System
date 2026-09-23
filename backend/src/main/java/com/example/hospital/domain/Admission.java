package com.example.hospital.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

@Entity
@Table(name = "admissions")
public class Admission extends DepartmentEntity {
  private String admissionNumber;
  private Long patientId;
  private Long attendingDoctorId;
  private Instant admissionDateTime;
  private Instant dischargeDateTime;
  private LocalDate expectedDischargeDate;
  private String status = "ACTIVE";
  private Long createdBy;
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "admission_room_requirements",
      joinColumns = @JoinColumn(name = "admission_id"),
      uniqueConstraints = @UniqueConstraint(columnNames = {"admission_id", "capability"}))
  @Column(name = "capability", nullable = false, length = 64)
  private Set<String> requiredRoomCapabilities = new TreeSet<>();

  public String getAdmissionNumber() { return admissionNumber; }
  public void setAdmissionNumber(String admissionNumber) { this.admissionNumber = admissionNumber; }
  public Long getPatientId() { return patientId; }
  public void setPatientId(Long patientId) { this.patientId = patientId; }
  public Long getAttendingDoctorId() { return attendingDoctorId; }
  public void setAttendingDoctorId(Long attendingDoctorId) { this.attendingDoctorId = attendingDoctorId; }
  public Instant getAdmissionDateTime() { return admissionDateTime; }
  public void setAdmissionDateTime(Instant admissionDateTime) { this.admissionDateTime = admissionDateTime; }
  public Instant getDischargeDateTime() { return dischargeDateTime; }
  public void setDischargeDateTime(Instant dischargeDateTime) { this.dischargeDateTime = dischargeDateTime; }
  public LocalDate getExpectedDischargeDate() { return expectedDischargeDate; }
  public void setExpectedDischargeDate(LocalDate expectedDischargeDate) { this.expectedDischargeDate = expectedDischargeDate; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Long getCreatedBy() { return createdBy; }
  public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
  public Set<String> getRequiredRoomCapabilities() { return requiredRoomCapabilities; }
  public void setRequiredRoomCapabilities(Set<String> requiredRoomCapabilities) {
    this.requiredRoomCapabilities =
        requiredRoomCapabilities == null ? new TreeSet<>() : new TreeSet<>(requiredRoomCapabilities);
  }
}
