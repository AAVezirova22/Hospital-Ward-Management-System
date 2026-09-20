package com.example.hospital.api;

import jakarta.validation.constraints.*;

public record AdmissionInput(
    @NotNull @Positive Long patientId,
    @NotNull @Positive Long doctorId,
    @NotNull @Positive Long roomId) {}
