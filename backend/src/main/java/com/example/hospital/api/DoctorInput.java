package com.example.hospital.api;

import jakarta.validation.constraints.*;

public record DoctorInput(
    @NotBlank @Size(max = 64) String doctorIdentifier,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Size(max = 100) String specialty,
    boolean active,
    Long version) {}
