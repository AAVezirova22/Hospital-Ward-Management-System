package com.example.hospital.api;

import com.example.hospital.service.OperationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OperationsController {
  private final OperationsService operations;
  private final com.example.hospital.service.OperationsStream stream;
  public OperationsController(OperationsService operations, com.example.hospital.service.OperationsStream stream) { this.operations = operations; this.stream = stream; }
  @GetMapping(value="/operations/stream", produces="text/event-stream")
  public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(jakarta.servlet.http.HttpServletResponse response) {
    // A proxy that compresses this response holds each event in its compression buffer, so the
    // browser opens the stream and then waits forever. no-transform tells any proxy in front of
    // the API to pass the bytes through; X-Accel-Buffering asks nginx not to buffer them either.
    response.setHeader("Cache-Control", "no-store, no-transform");
    response.setHeader("X-Accel-Buffering", "no");
    return stream.connect();
  }
  @GetMapping("/reports/operations")
  public Object overview() { return operations.overview(); }
  @GetMapping("/planner/simulate")
  public Object simulate(@RequestParam int arrivals) { return operations.simulate(arrivals); }
  public record Schedule(LocalDate date, @NotNull Long version) {}
  @PostMapping("/admissions/{id}/expected-discharge")
  public Object schedule(@PathVariable Long id, @Valid @RequestBody Schedule input) {
    return operations.schedule(id, input.date(), input.version());
  }
}
