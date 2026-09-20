package com.example.hospital.api;

import jakarta.validation.constraints.*;

public record RoomInput(
    @NotBlank @Size(max = 30) String roomNumber,
    @Min(1) @Max(100) int bedCount,
    boolean active,
    Long version) {}
