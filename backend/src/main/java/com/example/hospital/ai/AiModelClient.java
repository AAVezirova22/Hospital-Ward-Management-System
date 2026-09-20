package com.example.hospital.ai;

import java.util.*;

public interface AiModelClient {
  record ToolCall(String name, Map<String, String> arguments) {}

  record Context(
      String role, String route, Long selectedPatientId, List<Map<String, Object>> tools) {}

  ToolCall complete(String message, Context context);

  String identifier();
}
