package com.example.hospital.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientDraftInput(@NotBlank @Size(max = 64) String sourceId) {}
