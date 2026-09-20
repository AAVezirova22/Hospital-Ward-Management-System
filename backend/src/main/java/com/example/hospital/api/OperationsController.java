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
  public OperationsController(OperationsService operations) { this.operations = operations; }
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
