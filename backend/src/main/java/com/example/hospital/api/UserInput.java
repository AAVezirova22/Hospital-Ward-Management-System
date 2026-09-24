package com.example.hospital.api;

import jakarta.validation.constraints.*;

public record UserInput(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,64}") String username,
    @Size(max = 128) String password,
    @Pattern(regexp = "ADMIN|MEDICAL_STAFF|DOCTOR|PATIENT") @NotNull String role,
    boolean enabled,
    Long doctorId,
    Long version,
    @Size(max = 300) String reason) {}
