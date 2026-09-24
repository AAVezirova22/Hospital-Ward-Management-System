package com.example.hospital.api;

import com.example.hospital.ai.*;
import com.example.hospital.api.MessageInput;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AiController {
  private final AiAssistantService assistant;
  private final AiActionService actions;
  private final AiSourceService sources;
  private final AiPatientDraftService patientDrafts;

  public AiController(AiAssistantService a, AiActionService b, AiSourceService sources,
      AiPatientDraftService patientDrafts) {
    assistant = a;
    actions = b;
    this.sources = sources;
    this.patientDrafts = patientDrafts;
  }

  @GetMapping("/assistant/provider-disclosure")
  public Object providerDisclosure() { return patientDrafts.disclosure(); }

  @PostMapping("/assistant/patient-drafts")
  public Object patientDraft(@Valid @RequestBody PatientDraftInput input) {
    return patientDrafts.extract(input.sourceId());
  }

  @PostMapping(value = "/assistant/sources", consumes = "multipart/form-data")
  public Object upload(@RequestParam("file") MultipartFile file) {
    return sources.upload(file);
  }

  @DeleteMapping("/assistant/sources/{id}")
  public void removeSource(@PathVariable String id) { sources.remove(id); }

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
    return Views.pendingAction(actions.get(id));
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
