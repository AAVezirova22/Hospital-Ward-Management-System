package com.example.hospital.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record BedHoldInput(
    @Min(1) @Max(100) int bedCount,
    @NotBlank @Size(max = 500) String reason,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt) {}
