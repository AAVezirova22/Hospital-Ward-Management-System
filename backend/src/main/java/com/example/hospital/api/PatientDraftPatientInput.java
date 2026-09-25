package com.example.hospital.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PatientDraftPatientInput(@NotNull @Positive Long patientId) {}
