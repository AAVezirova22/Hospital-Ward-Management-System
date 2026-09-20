package com.example.hospital.ai;

import java.util.*;

public interface AiModelClient {
  record ToolCall(String name, Map<String, String> arguments) {}

  record Context(
      String role, String route, Long selectedPatientId, List<Map<String, Object>> tools,
      List<Map<String, String>> sources, List<com.example.hospital.api.Inputs.ConnectedFile> connectedFiles,
      List<Map<String, Object>> observations) {
    public Context(String role, String route, Long selectedPatientId, List<Map<String, Object>> tools) {
      this(role, route, selectedPatientId, tools, List.of(), List.of(), List.of());
    }
  }

  ToolCall complete(String message, Context context);

  String identifier();
}
