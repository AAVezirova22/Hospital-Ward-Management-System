package com.example.hospital.api;

import com.example.hospital.service.HospitalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StayController {
  private final HospitalService hospital;

  public StayController(HospitalService hospital) {
    this.hospital = hospital;
  }

  @GetMapping("/admissions")
  public Object admissions() {
    return hospital.admissions().stream().map(hospital::admissionView).toList();
  }

  @GetMapping("/admissions/{id}")
  public Object admission(@PathVariable Long id) {
    return hospital.admissionView(hospital.admission(id));
  }

  @PostMapping("/admissions")
  @ResponseStatus(HttpStatus.CREATED)
  public Object admit(@Valid @RequestBody AdmissionInput in) {
    return hospital.admit(in, "UI");
  }

  @PostMapping("/admissions/{id}/transfer")
  public Object transfer(@PathVariable Long id, @Valid @RequestBody TransferInput in) {
    return hospital.transfer(id, in, "UI");
  }

  @PostMapping("/admissions/{id}/discharge")
  public Object discharge(@PathVariable Long id, @Valid @RequestBody DischargeInput in) {
    return hospital.discharge(id, in.version(), "UI");
  }

  public record DoctorChange(
      @jakarta.validation.constraints.NotNull Long doctorId,
      @jakarta.validation.constraints.NotNull Long version) {}

  @PostMapping("/admissions/{id}/doctor")
  public Object doctor(@PathVariable Long id, @Valid @RequestBody DoctorChange in) {
    return hospital.changeDoctor(id, in.doctorId(), in.version());
  }

  @PostMapping("/admissions/{id}/procedures")
  @ResponseStatus(HttpStatus.CREATED)
  public Object record(@PathVariable Long id, @Valid @RequestBody RecordProcedureInput in) {
    return hospital.recordProcedure(id, in);
  }
}
