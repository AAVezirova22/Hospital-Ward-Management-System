package com.example.hospital.api;

import jakarta.validation.constraints.*;

public record MessageInput(
    @Size(max = 64) String sessionId,
    @NotBlank @Size(max = 2000) String message,
    @Size(max = 120) String route,
    Long selectedPatientId) {}
