package com.example.hospital.api;

import com.example.hospital.service.PatientCorrectionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Patient-submitted demographic correction requests (#159).
 *
 * <p>{@code /portal/**} is already restricted to the PATIENT role in SecurityConfig, so
 * these patient endpoints inherit that. The staff review endpoints live under
 * {@code /patient-corrections} and require staff, enforced in the service.
 */
@RestController
@RequestMapping("/api/v1")
public class PatientCorrectionController {
  private final PatientCorrectionService corrections;

  public PatientCorrectionController(PatientCorrectionService corrections) {
    this.corrections = corrections;
  }

  @GetMapping("/portal/correction-requests")
  public Object myRequests() {
    return corrections.myRequests();
  }

  @GetMapping("/portal/correction-fields")
  public Object fields() {
    return PatientCorrectionService.supportedFields();
  }

  @PostMapping("/portal/correction-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public Object submit(@Valid @RequestBody PatientCorrectionService.RequestInput in) {
    return corrections.submit(in);
  }

  @GetMapping("/patient-correction-requests")
  public Object queue(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return corrections.queue(status, page, size);
  }

  @PostMapping("/patient-correction-requests/{id}/approve")
  public Object approve(
      @PathVariable long id, @Valid @RequestBody PatientCorrectionService.ReviewInput in) {
    return corrections.review(id, true, in);
  }

  @PostMapping("/patient-correction-requests/{id}/reject")
  public Object reject(
      @PathVariable long id, @Valid @RequestBody PatientCorrectionService.ReviewInput in) {
    return corrections.review(id, false, in);
  }
}
