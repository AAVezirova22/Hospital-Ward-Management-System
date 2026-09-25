package com.example.hospital.api;

import com.example.hospital.service.PatientAccountLinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patient-account-links")
@PreAuthorize("hasRole('ADMIN')")
public class PatientAccountLinkController {
  public record LinkInput(long patientId, @NotBlank String evidence) {}
  private final PatientAccountLinkService links;
  public PatientAccountLinkController(PatientAccountLinkService links) { this.links = links; }
  @GetMapping public Object queue() { return links.queue(); }
  @PostMapping("/{userId}") public Object link(@PathVariable long userId, @Valid @RequestBody LinkInput input) {
    return links.link(userId, input.patientId(), input.evidence());
  }
}
