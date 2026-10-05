package com.example.hospital.api;

import jakarta.validation.constraints.*;

public record AppointmentInput(
    @NotNull @Positive Long doctorId,
    @NotBlank @Size(max = 120) String attendeeName,
    @NotBlank @Size(max = 40) String startsAt,
    @Min(5) @Max(240) Integer durationMinutes,
    @Size(max = 120) String contact,
    @Size(max = 500) String notes) {}
