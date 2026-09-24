package com.example.hospital.api;

import com.example.hospital.service.SecurityEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/security/events")
@PreAuthorize("hasRole('ADMIN')")
public class SecurityEventController {
  private final SecurityEventService events;

  public SecurityEventController(SecurityEventService events) {
    this.events = events;
  }

  public record StatusInput(@NotBlank @Size(max = 20) String status, @Size(max = 300) String note) {}

  @GetMapping
  public Object inbox(
      @RequestParam(defaultValue = "ACTIVE") String status,
      @RequestParam(defaultValue = "false") boolean includeInfo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return events.inbox(status, includeInfo, page, size);
  }

  @PostMapping("/{id}/acknowledge")
  public Object acknowledge(@PathVariable long id) {
    return events.acknowledge(id);
  }

  @PutMapping("/{id}")
  public Object update(@PathVariable long id, @Valid @RequestBody StatusInput input) {
    return events.updateStatus(id, input.status(), input.note());
  }
}
