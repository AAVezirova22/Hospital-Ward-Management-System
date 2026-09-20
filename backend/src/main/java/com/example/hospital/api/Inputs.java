package com.example.hospital.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;

public class Inputs {
  public record PatientInput(
      @NotBlank @Size(max = 64) String patientIdentifier,
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @NotNull @PastOrPresent LocalDate dateOfBirth,
      @Size(max = 500) String address,
      @Size(max = 40) String phoneNumber,
      Long version) {}

  public record DoctorInput(
      @NotBlank @Size(max = 64) String doctorIdentifier,
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @NotBlank @Size(max = 100) String specialty,
      boolean active,
      Long version) {}

  public record RoomInput(
      @NotBlank @Size(max = 30) String roomNumber,
      @Min(1) @Max(100) int bedCount,
      boolean active,
      Long version) {}

  public record ProcedureInput(
      @NotBlank @Size(max = 64) String procedureCode,
      @NotBlank @Size(max = 150) String procedureName,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal currentCost,
      boolean active,
      Long version) {}

  public record UserInput(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,64}") String username,
      @Size(max = 128) String password,
      @Pattern(regexp = "ADMIN|MEDICAL_STAFF|DOCTOR") @NotNull String role,
      boolean enabled,
      Long doctorId,
      Long version) {}

  public record AdmissionInput(
      @NotNull @Positive Long patientId,
      @NotNull @Positive Long doctorId,
      @NotNull @Positive Long roomId) {}

  public record TransferInput(
      @NotNull @Positive Long roomId,
      @NotBlank @Size(max = 500) String reason,
      @NotNull Long version) {}

  public record DischargeInput(@NotNull Long version) {}

  public record RecordProcedureInput(
      @NotNull Long medicalProcedureId,
      @NotNull Long doctorId,
      @NotNull @PastOrPresent Instant performedAt,
      @Size(max = 2000) String note) {}

  public record MessageInput(
      @Size(max = 64) String sessionId,
      @NotBlank @Size(max = 2000) String message,
      @Size(max = 120) String route,
      Long selectedPatientId) {}
}
