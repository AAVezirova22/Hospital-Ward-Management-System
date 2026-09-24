package com.example.hospital.api;

import com.example.hospital.service.AiUsageReportService;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/ai-usage")
@PreAuthorize("hasRole('ADMIN')")
public class AiUsageController {
  private final AiUsageReportService usage;

  public AiUsageController(AiUsageReportService usage) {
    this.usage = usage;
  }

  @GetMapping
  public Object report(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
    return usage.report(from, to);
  }
}
