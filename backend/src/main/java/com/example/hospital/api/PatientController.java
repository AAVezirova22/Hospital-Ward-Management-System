package com.example.hospital.api;

import com.example.hospital.api.PatientInput;
import com.example.hospital.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PatientController {
  private final PatientService patients;

  public PatientController(PatientService patients) {
    this.patients = patients;
  }

  @GetMapping("/patients")
  public Object patients(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Boolean activeAdmission,
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) Long roomId) {
    return patients.list(q, activeAdmission, doctorId, roomId).stream()
        .map(Views.PatientDirectory::of)
        .toList();
  }

  @GetMapping("/patients/{id}")
  public Object patient(@PathVariable String id) {
    return patients.summary(id);
  }

  @PostMapping("/patients")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addPatient(@Valid @RequestBody PatientInput in) {
    return Views.patient(patients.save(null, in));
  }

  @PutMapping("/patients/{id}")
  public Object editPatient(@PathVariable Long id, @Valid @RequestBody PatientInput in) {
    return Views.patient(patients.save(id, in));
  }
}
