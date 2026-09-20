package com.example.hospital.api;

import com.example.hospital.api.Inputs.UserInput;
import com.example.hospital.repository.AuditEventRepository;
import com.example.hospital.service.UserService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
  private final UserService users;
  private final AuditEventRepository audit;

  public AccountController(UserService users, AuditEventRepository audit) {
    this.users = users;
    this.audit = audit;
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public Object users() {
    return users.list();
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public Object user(@Valid @RequestBody UserInput in) {
    return users.save(null, in);
  }

  @PutMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object user(@PathVariable Long id, @Valid @RequestBody UserInput in) {
    return users.save(id, in);
  }

  @GetMapping("/audit")
  @PreAuthorize("hasRole('ADMIN')")
  public Object audit(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String eventType) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);
    var sort = org.springframework.data.domain.Sort.by("timestamp").descending();
    var request = org.springframework.data.domain.PageRequest.of(safePage, safeSize, sort);
    var all =
        eventType == null || eventType.isBlank()
            ? audit.findAll(request)
            : audit.findByEventTypeIgnoreCase(eventType.strip(), request);
    return Map.of(
        "page", safePage,
        "size", safeSize,
        "total", all.getTotalElements(),
        "events", all.getContent());
  }
}
