package com.example.hospital.api;

import com.example.hospital.service.RetentionService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retention")
@PreAuthorize("hasRole('ADMIN')")
public class RetentionController {
  private final RetentionService retention;

  public RetentionController(RetentionService retention) {
    this.retention = retention;
  }

  @GetMapping("/preview")
  public Object preview() {
    return retention.preview();
  }

  @PostMapping("/apply")
  public Object apply(@RequestBody Map<String, String> input) {
    return retention.apply(input.get("confirmation"));
  }
}
