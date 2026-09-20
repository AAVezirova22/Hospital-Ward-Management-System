package com.example.hospital.api;
import com.example.hospital.security.Actor;
import com.example.hospital.service.PatientService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/portal")
public class PortalController {
  private final Actor actor; private final PatientService patients;
  public PortalController(Actor actor,PatientService patients) {this.actor=actor;this.patients=patients;}
  @GetMapping("/me") public Object me() {return patients.summary(actor.user().getPatientId());}
}
