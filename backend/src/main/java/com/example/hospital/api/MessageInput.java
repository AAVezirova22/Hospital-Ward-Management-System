package com.example.hospital.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record MessageInput(
    @Size(max = 64) String sessionId,
    @NotBlank @Size(max = 2000) String message,
    @Size(max = 120) String route,
    Long selectedPatientId,
    @Size(max = 20) List<@NotBlank @Size(max = 64) String> sourceIds,
    @Size(max = 300) List<@Valid ConnectedFile> connectedFiles,
    @Size(max = 64) String retryToken) {
  public MessageInput(String sessionId, String message, String route, Long selectedPatientId) {
    this(sessionId, message, route, selectedPatientId, List.of(), List.of(), null);
  }

  public MessageInput(String sessionId, String message, String route, Long selectedPatientId,
      List<String> sourceIds, List<ConnectedFile> connectedFiles) {
    this(sessionId, message, route, selectedPatientId, sourceIds, connectedFiles, null);
  }

  public record ConnectedFile(
      @NotBlank @Size(max = 64) String id, @NotBlank @Size(max = 300) String name) {}
}
