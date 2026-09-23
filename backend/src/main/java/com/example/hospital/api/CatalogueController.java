package com.example.hospital.api;

import com.example.hospital.service.CatalogueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogueController {
  private final CatalogueService catalogue;

  public CatalogueController(CatalogueService catalogue) {
    this.catalogue = catalogue;
  }

  @GetMapping("/doctors")
  public Object doctors() {
    return catalogue.doctors().stream().map(Views::doctor).toList();
  }

  @PostMapping("/doctors")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addDoctor(@Valid @RequestBody DoctorInput in) {
    return Views.doctor(catalogue.saveDoctor(null, in));
  }

  @PutMapping("/doctors/{id}")
  public Object editDoctor(@PathVariable Long id, @Valid @RequestBody DoctorInput in) {
    return Views.doctor(catalogue.saveDoctor(id, in));
  }

  @GetMapping("/rooms")
  public Object rooms(
      @RequestParam(defaultValue = "0") int minFree,
      @RequestParam(required = false) java.util.List<String> requiredCapabilities) {
    return catalogue.rooms(minFree, requiredCapabilities);
  }

  @PostMapping("/rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addRoom(@Valid @RequestBody RoomInput in) {
    return Views.room(catalogue.saveRoom(null, in));
  }

  @PutMapping("/rooms/{id}")
  public Object editRoom(@PathVariable Long id, @Valid @RequestBody RoomInput in) {
    return Views.room(catalogue.saveRoom(id, in));
  }

  @GetMapping("/procedures")
  public Object procedures() {
    return catalogue.procedures().stream().map(Views::procedure).toList();
  }

  @PostMapping("/procedures")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addProcedure(@Valid @RequestBody ProcedureInput in) {
    return Views.procedure(catalogue.saveProcedure(null, in));
  }

  @PutMapping("/procedures/{id}")
  public Object editProcedure(@PathVariable Long id, @Valid @RequestBody ProcedureInput in) {
    return Views.procedure(catalogue.saveProcedure(id, in));
  }
}
