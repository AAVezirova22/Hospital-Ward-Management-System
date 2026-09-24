package com.example.hospital.api;

import com.example.hospital.security.DepartmentContext;
import com.example.hospital.service.WorkspaceService;
import com.example.hospital.service.DepartmentTimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
  private final WorkspaceService workspaces;
  private final DepartmentTimeService departmentTime;
  public WorkspaceController(WorkspaceService workspaces, DepartmentTimeService departmentTime) {
    this.workspaces = workspaces;
    this.departmentTime = departmentTime;
  }
  public record HospitalInput(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=120) String departmentName) {}
  public record DepartmentInput(@NotBlank @Size(max=120) String name) {}
  public record TimeZoneInput(@NotBlank @Size(max=64) String timeZone) {}
  public record JoinInput(@NotBlank @Size(max=40) String code) {}
  public record OwnerInput(@NotNull Long userId) {}
  public record RotateInput(Integer expiresInHours, Boolean singleUse) {}
  public record RoleInput(@NotNull Long userId, @NotBlank @Size(max=30) String role, Long doctorId) {}
  public record PatientImportPermissionInput(@NotNull Boolean enabled) {}

  @GetMapping
  public Object list() { return Map.of("activeDepartmentId", DepartmentContext.id(), "timeZone", departmentTime.timeZone(), "hospitals", workspaces.list()); }
  @GetMapping("/hospitals/{id}/members")
  public Object hospitalMembers(@PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return workspaces.hospitalMembers(id, page, size);
  }
  @GetMapping("/departments/{id}/members")
  public Object departmentMembers(@PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return workspaces.departmentMembers(id, page, size);
  }
  @PostMapping("/hospitals") @ResponseStatus(HttpStatus.CREATED)
  public Object create(@Valid @RequestBody HospitalInput input) {
    return workspaces.createHospital(input.name(), input.departmentName());
  }
  @PostMapping("/hospitals/{id}/departments") @ResponseStatus(HttpStatus.CREATED)
  public Object department(@PathVariable long id, @Valid @RequestBody DepartmentInput input) {
    var created = workspaces.createDepartment(id, input.name());
    return Map.of("hospitalId", id, "departmentId", created.get("departmentId"), "joinCode", created.get("joinCode"));
  }
  @PutMapping("/departments/{id}/timezone")
  public Object timeZone(@PathVariable long id, @Valid @RequestBody TimeZoneInput input) {
    return workspaces.setTimeZone(id, input.timeZone());
  }
  @PostMapping("/join")
  public Object join(@Valid @RequestBody JoinInput input, HttpServletRequest request) {
    return workspaces.join(input.code(), request.getRemoteAddr());
  }
  @GetMapping("/hospitals/{id}/code")
  public Object revealHospital(@PathVariable long id) {
    return Map.of("code", workspaces.reveal(true, id));
  }
  @GetMapping("/departments/{id}/code")
  public Object revealDepartment(@PathVariable long id) {
    return Map.of("code", workspaces.reveal(false, id));
  }
  @PostMapping("/hospitals/{id}/code")
  public Object hospitalCode(@PathVariable long id, @RequestBody(required = false) RotateInput input) {
    return Map.of("code", workspaces.rotate(true, id, hours(input), singleUse(input)));
  }
  @PostMapping("/departments/{id}/code")
  public Object departmentCode(@PathVariable long id, @RequestBody(required = false) RotateInput input) {
    return Map.of("code", workspaces.rotate(false, id, hours(input), singleUse(input)));
  }
  @PostMapping("/hospitals/{id}/leave")
  public void leaveHospital(@PathVariable long id) { workspaces.leaveHospital(id); }
  @PostMapping("/departments/{id}/leave")
  public void leaveDepartment(@PathVariable long id) { workspaces.leaveDepartment(id); }
  @DeleteMapping("/hospitals/{id}/members/{userId}")
  public void revokeHospital(@PathVariable long id, @PathVariable long userId) { workspaces.revokeHospital(id, userId); }
  @DeleteMapping("/departments/{id}/members/{userId}")
  public void revokeDepartment(@PathVariable long id, @PathVariable long userId) { workspaces.revokeDepartment(id, userId); }
  @PostMapping("/hospitals/{id}/owners")
  public void grantOwner(@PathVariable long id, @Valid @RequestBody OwnerInput input) {
    workspaces.grantOwner(id, input.userId());
  }
  @PostMapping("/departments/{id}/roles")
  public Object grantRole(@PathVariable long id, @Valid @RequestBody RoleInput input) {
    return workspaces.grantRole(id, input.userId(), input.role(), input.doctorId());
  }
  @PutMapping("/departments/{id}/members/{userId}/patient-import")
  public Object patientImportPermission(@PathVariable long id, @PathVariable long userId,
      @Valid @RequestBody PatientImportPermissionInput input) {
    return workspaces.setPatientImportPermission(id, userId, input.enabled());
  }

  private static Integer hours(RotateInput input) {
    return input == null ? null : input.expiresInHours();
  }

  private static boolean singleUse(RotateInput input) {
    return input != null && Boolean.TRUE.equals(input.singleUse());
  }
}
