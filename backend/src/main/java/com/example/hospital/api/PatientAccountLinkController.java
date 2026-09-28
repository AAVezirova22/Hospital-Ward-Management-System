package com.example.hospital.api;

import com.example.hospital.service.PatientAccountLinkService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Administrator-reviewed linking of self-registered portal accounts to real patient
 * records (#160). Every action is admin-only, enforced in the service.
 */
@RestController
@RequestMapping("/api/v1/patient-account-links")
public class PatientAccountLinkController {
  private final PatientAccountLinkService links;

  public PatientAccountLinkController(PatientAccountLinkService links) {
    this.links = links;
  }

  @GetMapping("/candidates")
  public Object candidates(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return links.queue(page, size);
  }

  @PostMapping("/{userId}")
  @ResponseStatus(HttpStatus.CREATED)
  public Object request(
      @PathVariable long userId, @Valid @RequestBody PatientAccountLinkService.LinkInput in) {
    return links.request(userId, in);
  }

  @PostMapping("/{linkId}/approve")
  public Object approve(
      @PathVariable long linkId,
      @Valid @RequestBody PatientAccountLinkService.ReviewInput in) {
    return links.review(linkId, true, in);
  }

  @PostMapping("/{linkId}/reject")
  public Object reject(
      @PathVariable long linkId,
      @Valid @RequestBody PatientAccountLinkService.ReviewInput in) {
    return links.review(linkId, false, in);
  }
}
