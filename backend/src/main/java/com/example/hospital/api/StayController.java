package com.example.hospital.api;

import com.example.hospital.service.StayService;
import jakarta.servlet.http.HttpServletRequest;
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
  public org.springframework.http.ResponseEntity<PagedResult<Map<String, Object>>> admissions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction,
      HttpServletRequest request) {
    // A single "sort=key:direction" keeps the register's sort in one place in the
    // URL, which is what makes the current sort shareable and bookmarkable.
    String sortKey = null;
    String sortDirection = direction;
    if (sort != null && !sort.isBlank()) {
      String[] parsed = com.example.hospital.service.AdmissionSort.parse(sort);
      sortKey = parsed[0];
      if (sortDirection == null || sortDirection.isBlank()) sortDirection = parsed[1];
    }
    var result = stays.list(page, size, search, status, from, to, doctorId, sortKey, sortDirection);
    var paged = PagedResult.of(result.map(stays::view));

    // This endpoint echoes the sort actually applied, including the default, so the
    // control a client renders is the sort the server used and not one it guessed.
    // The echo is a header rather than a body field: adding fields to the body would
    // stop PageLinks from recognising a PagedResult, and the pagination headers are
    // part of this endpoint's published contract (#164 keeps it identical to every
    // other list).
    var headers = new org.springframework.http.HttpHeaders();
    PageLinks.apply(headers,
        java.net.URI.create(request.getRequestURL().toString()),
        paged.page(), paged.size(), paged.totalElements());
    headers.set("X-Applied-Sort", stays.appliedSort(sortKey, sortDirection));
    return org.springframework.http.ResponseEntity.ok().headers(headers).body(paged);
  }

  /** The sortable columns and their default directions (#164). */
  @GetMapping("/admissions/sort-options")
  public Object admissionSortOptions() {
    return stays.sortOptions();
  }

  /**
   * The cleared filter set, so a client does not have to hard-code what "no
   * filters" means and can reset the form from the server's own definition.
   */
  @GetMapping("/admissions/filters")
  public Object admissionFilters() {
    return stays.defaultFilters();
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
