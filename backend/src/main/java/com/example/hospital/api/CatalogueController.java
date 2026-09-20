package com.example.hospital.api;

import com.example.hospital.api.Inputs.*;
import com.example.hospital.service.HospitalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogueController {
  private final HospitalService hospital;

  public CatalogueController(HospitalService hospital) {
    this.hospital = hospital;
  }

  @GetMapping("/doctors")
  public Object doctors() {
    return hospital.doctors();
  }

  @PostMapping("/doctors")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addDoctor(@Valid @RequestBody DoctorInput in) {
    return hospital.saveDoctor(null, in);
  }

  @PutMapping("/doctors/{id}")
  public Object editDoctor(@PathVariable Long id, @Valid @RequestBody DoctorInput in) {
    return hospital.saveDoctor(id, in);
  }

  @GetMapping("/rooms")
  public Object rooms(@RequestParam(defaultValue = "0") int minFree) {
    return hospital.rooms(minFree);
  }

  @PostMapping("/rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addRoom(@Valid @RequestBody RoomInput in) {
    return hospital.saveRoom(null, in);
  }

  @PutMapping("/rooms/{id}")
  public Object editRoom(@PathVariable Long id, @Valid @RequestBody RoomInput in) {
    return hospital.saveRoom(id, in);
  }

  @GetMapping("/procedures")
  public Object procedures() {
    return hospital.procedures();
  }

  @PostMapping("/procedures")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addProcedure(@Valid @RequestBody ProcedureInput in) {
    return hospital.saveProcedure(null, in);
  }

  @PutMapping("/procedures/{id}")
  public Object editProcedure(@PathVariable Long id, @Valid @RequestBody ProcedureInput in) {
    return hospital.saveProcedure(id, in);
  }
}
