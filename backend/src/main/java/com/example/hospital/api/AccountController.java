package com.example.hospital.api;

import com.example.hospital.service.AuditEventQueryService;
import com.example.hospital.service.UserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
  private final UserService users;
  private final AuditEventQueryService audit;

  public AccountController(UserService users, AuditEventQueryService audit) {
    this.users = users;
    this.audit = audit;
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public Object users() {
    return users.list().stream().map(Views::account).toList();
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public Object user(@Valid @RequestBody UserInput in) {
    return Views.account(users.save(null, in));
  }

  @PutMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object user(@PathVariable Long id, @Valid @RequestBody UserInput in) {
    return Views.account(users.save(id, in));
  }

  @GetMapping("/audit")
  @PreAuthorize("hasRole('ADMIN')")
  public Object audit(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Long actorId,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) Long entityId,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);
    var request = PageRequest.of(
        safePage, safeSize, Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id")));
    var filters = new AuditEventQueryService.Filters(
        eventType, actorId, entityType, entityId, source, from, to);
    var all = audit.page(filters, request);
    return Map.of(
        "page", safePage,
        "size", safeSize,
        "total", all.getTotalElements(),
        "events", all.getContent().stream().map(Views::audit).toList());
  }
}
