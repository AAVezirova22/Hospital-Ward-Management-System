package com.example.hospital.api;

import jakarta.validation.constraints.*;
import java.util.List;

public record AdmissionInput(
    @NotNull @Positive Long patientId,
    @NotNull @Positive Long doctorId,
    @NotNull @Positive Long roomId,
    @Size(max = 30) List<@NotBlank @Size(max = 64) String> requiredRoomCapabilities,
    @Size(max = 64) String bedIdentifier) {
  public AdmissionInput(Long patientId, Long doctorId, Long roomId) {
    this(patientId, doctorId, roomId, List.of(), null);
  }
  public AdmissionInput(Long patientId, Long doctorId, Long roomId, List<String> requiredRoomCapabilities) {
    this(patientId, doctorId, roomId, requiredRoomCapabilities, null);
  }
}
