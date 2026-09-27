package com.example.hospital.api;

import com.example.hospital.security.MaintenanceMode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public so the sign-in screen can show the maintenance notice before anyone authenticates. */
@RestController
public class MaintenanceController {
  private final MaintenanceMode maintenance;

  public MaintenanceController(MaintenanceMode maintenance) {
    this.maintenance = maintenance;
  }

  @GetMapping("/api/v1/maintenance")
  public Object status() {
    return maintenance.status();
  }
}
