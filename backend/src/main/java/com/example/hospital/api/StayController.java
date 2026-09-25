package com.example.hospital.api;

import com.example.hospital.service.StayService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StayController {
  private final StayService stays;

  public StayController(StayService stays) {
    this.stays = stays;
  }

  @GetMapping("/admissions")
  public PagedResult<Map<String, Object>> admissions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) String q) {
    var result = stays.list(page, size, status, from, to, doctorId, q);
    return PagedResult.of(result.map(stays::view));
  }

  @GetMapping("/admissions/{id}")
  public Object admission(@PathVariable Long id) {
    return stays.view(id);
  }

  @PostMapping("/admissions")
  @ResponseStatus(HttpStatus.CREATED)
  public Object admit(@Valid @RequestBody AdmissionInput in) {
    return stays.admit(in);
  }

  @PostMapping("/admissions/{id}/transfer")
  public Object transfer(@PathVariable Long id, @Valid @RequestBody TransferInput in) {
    return stays.transfer(id, in);
  }

  @PostMapping("/admissions/{id}/discharge")
  public Object discharge(@PathVariable Long id, @Valid @RequestBody DischargeInput in) {
    return stays.discharge(id, in);
  }

  public record DoctorChange(
      @jakarta.validation.constraints.NotNull Long doctorId,
      @jakarta.validation.constraints.NotNull Long version) {}

  @PostMapping("/admissions/{id}/doctor")
  public Object doctor(@PathVariable Long id, @Valid @RequestBody DoctorChange in) {
    return stays.changeDoctor(id, in.doctorId(), in.version());
  }

  @PostMapping("/admissions/{id}/procedures")
  @ResponseStatus(HttpStatus.CREATED)
  public Object record(@PathVariable Long id, @Valid @RequestBody RecordProcedureInput in) {
    return stays.recordProcedure(id, in);
  }
}
