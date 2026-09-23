package com.example.hospital.api;

import jakarta.validation.constraints.*;
import java.util.List;

public record RoomInput(
    @NotBlank @Size(max = 30) String roomNumber,
    @Min(1) @Max(100) int bedCount,
    boolean active,
    @Size(max = 30) List<@NotBlank @Size(max = 64) String> capabilities,
    Long version) {
  public RoomInput(String roomNumber, int bedCount, boolean active, Long version) {
    this(roomNumber, bedCount, active, List.of(), version);
  }
}
