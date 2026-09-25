package com.example.hospital.api;

import com.example.hospital.service.PatientCorrectionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patient-correction-requests")
public class PatientCorrectionController {
  private final PatientCorrectionService corrections;
  public PatientCorrectionController(PatientCorrectionService corrections) { this.corrections = corrections; }
  @GetMapping public Object queue() { return corrections.queue(); }
  @PostMapping("/{id}/decision") public Object decide(@PathVariable long id, @RequestBody PatientCorrectionService.DecisionInput input) {
    return corrections.decide(id, input);
  }
}
