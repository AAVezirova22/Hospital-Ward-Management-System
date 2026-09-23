package com.example.hospital.api;

import com.example.hospital.service.CatalogueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogueController {
  private final CatalogueService catalogue;
  private final com.example.hospital.service.BedHoldService bedHolds;

  public CatalogueController(
      CatalogueService catalogue, com.example.hospital.service.BedHoldService bedHolds) {
    this.catalogue = catalogue;
    this.bedHolds = bedHolds;
  }

  @GetMapping("/doctors")
  public Object doctors(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Boolean active) {
    return catalogue.doctorPage(q, page, size, active);
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
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int minFree,
      @RequestParam(required = false) Long roomId,
      @RequestParam(required = false) java.util.List<String> requiredCapabilities) {
    return catalogue.roomPage(q, page, size, active, minFree, roomId, requiredCapabilities);
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

  @PostMapping("/rooms/{id}/holds")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addBedHold(@PathVariable Long id, @Valid @RequestBody BedHoldInput in) {
    return bedHolds.create(id, in);
  }

  @DeleteMapping("/rooms/{roomId}/holds/{holdId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelBedHold(@PathVariable Long roomId, @PathVariable Long holdId) {
    bedHolds.cancel(roomId, holdId);
  }

  @GetMapping("/procedures")
  public Object procedures(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Boolean active) {
    return catalogue.procedurePage(q, page, size, active);
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
