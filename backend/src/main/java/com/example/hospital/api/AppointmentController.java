package com.example.hospital.api;

import com.example.hospital.service.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AppointmentController {
  private final AppointmentService appointments;
  public AppointmentController(AppointmentService appointments) { this.appointments = appointments; }

  @GetMapping("/appointments")
  public Object list(@RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "SCHEDULED") String status, @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return appointments.list(doctorId, from, to, status, q, page, size);
  }

  @GetMapping("/doctors/{doctorId}/availability")
  public Object availability(@PathVariable Long doctorId, @RequestParam String startsAt,
      @RequestParam(required = false) Integer durationMinutes) {
    return appointments.availability(doctorId, startsAt, durationMinutes);
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  public Object create(@Valid @RequestBody AppointmentInput input) {
    return appointments.create(input, "UI");
  }

  public record CancellationInput(@NotNull @PositiveOrZero Long version) {}

  @PostMapping("/appointments/{id}/cancel")
  public Object cancel(@PathVariable Long id, @Valid @RequestBody CancellationInput input) {
    return appointments.cancel(id, input.version());
  }
}
