package com.example.hospital.api;

import com.example.hospital.ai.*;
import com.example.hospital.api.MessageInput;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AiController {
  private final AiAssistantService assistant;
  private final AiActionService actions;

  public AiController(AiAssistantService a, AiActionService b) {
    assistant = a;
    actions = b;
  }

  @PostMapping("/assistant/messages")
  public Object message(@Valid @RequestBody MessageInput in) {
    return assistant.message(in);
  }

  @GetMapping("/assistant/sessions/{key}")
  public Object history(@PathVariable String key) {
    return assistant.history(key);
  }

  @PostMapping("/assistant/sessions/{key}/clear")
  public void clear(@PathVariable String key) {
    assistant.clear(key);
  }

  @GetMapping("/ai-actions/{id}")
  public Object action(@PathVariable Long id) {
    return actions.get(id);
  }

  @PostMapping("/ai-actions/{id}/confirm")
  public Object confirm(@PathVariable Long id) {
    return actions.confirm(id);
  }

  @PostMapping("/ai-actions/{id}/cancel")
  public Object cancel(@PathVariable Long id) {
    return actions.cancel(id);
  }
}
