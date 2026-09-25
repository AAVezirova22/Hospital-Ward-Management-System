package com.example.hospital.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CareWorkflowInput(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 1000) String description,
    @NotNull @Size(min = 1, max = 3) List<String> triggers,
    @NotNull @Size(min = 1, max = 100) List<@Valid Task> tasks,
    @NotNull Long version) {
  public record Task(
      @NotBlank @Size(max = 80) String key,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 2000) String description,
      @NotBlank @Size(max = 30) String ownerRole,
      Long assignedUserId,
      @NotNull Long dueOffsetMinutes,
      @NotNull @Size(max = 100) List<String> dependsOn) {}
}
