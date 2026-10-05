package com.example.hospital.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "doctor_appointments")
public class DoctorAppointment extends DepartmentEntity {
  private Long doctorId;
  private String attendeeName;
  private String contact;
  private String notes;
  private Instant startsAt;
  private Instant endsAt;
  private Long createdBy;
  private Instant cancelledAt;
  private Long cancelledBy;

  public Long getDoctorId() { return doctorId; }
  public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
  public String getAttendeeName() { return attendeeName; }
  public void setAttendeeName(String attendeeName) { this.attendeeName = attendeeName; }
  public String getContact() { return contact; }
  public void setContact(String contact) { this.contact = contact; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
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
