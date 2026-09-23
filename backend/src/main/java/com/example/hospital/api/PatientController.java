package com.example.hospital.api;

import com.example.hospital.api.PatientInput;
import com.example.hospital.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Validated
public class PatientController {
  public record DirectoryPage(
      List<java.util.Map<String, Object>> items,
      int page,
      int size,
      long totalElements,
      int totalPages,
      boolean hasNext,
      Integer nextPage) {}

  private final PatientService patients;

  public PatientController(PatientService patients) {
    this.patients = patients;
  }

  @GetMapping("/patients")
  public DirectoryPage patients(
      @RequestParam(defaultValue = "") @Size(max = 100) String q,
      @RequestParam(required = false) Boolean activeAdmission,
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) Long roomId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    var results = patients.list(q, activeAdmission, doctorId, roomId, page, size);
    boolean hasNext = results.hasNext();
    return new DirectoryPage(
        results.getContent().stream().map(Views.PatientDirectory::of).toList(),
        results.getNumber(),
        results.getSize(),
        results.getTotalElements(),
        results.getTotalPages(),
        hasNext,
        hasNext ? results.getNumber() + 1 : null);
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
