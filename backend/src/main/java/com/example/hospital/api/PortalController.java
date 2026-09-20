package com.example.hospital.api;
import com.example.hospital.security.Actor;
import com.example.hospital.service.HospitalService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/portal")
public class PortalController {
  private final Actor actor; private final HospitalService hospital;
  public PortalController(Actor actor,HospitalService hospital) {this.actor=actor;this.hospital=hospital;}
  @GetMapping("/me") public Object me() {return hospital.summary(actor.user().patientId);}
}
