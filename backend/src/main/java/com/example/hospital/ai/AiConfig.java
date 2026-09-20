package com.example.hospital.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class AiConfig {
  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  @Bean
  AiModelClient model(
      @Value("${app.ai.mode}") String mode,
      @Value("${app.ai.url}") String url,
      @Value("${app.ai.key}") String key,
      @Value("${app.ai.model}") String name,
      @Value("${app.ai.timeout-seconds}") int timeout,
      ObjectMapper json) {
    return switch (mode) {
      case "local" -> new LocalModelClient();
      case "external" -> {
        if (url.isBlank() || name.isBlank())
          throw new IllegalArgumentException("AI_URL and AI_MODEL required");
        yield new ExternalAiProviderClient(url, key, name, timeout, json);
      }
      case "off" ->
          new AiModelClient() {
            public String identifier() {
              return "disabled";
            }

            public ToolCall complete(String m, Context c) {
              throw new IllegalStateException("Disabled");
            }
          };
      default -> throw new IllegalArgumentException("Unknown AI_MODE");
    };
  }
}
