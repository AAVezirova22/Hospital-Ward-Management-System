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
  private final com.example.hospital.security.ClientAddressResolver clientAddresses;
  public WorkspaceController(
      WorkspaceService workspaces,
      DepartmentTimeService departmentTime,
      com.example.hospital.security.ClientAddressResolver clientAddresses) {
    this.workspaces = workspaces;
    this.departmentTime = departmentTime;
    this.clientAddresses = clientAddresses;
  }
  public record HospitalInput(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=120) String departmentName) {}
  public record DepartmentInput(@NotBlank @Size(max=120) String name) {}
  public record TimeZoneInput(@NotBlank @Size(max=64) String timeZone) {}
  public record JoinInput(@NotBlank @Size(max=40) String code) {}
  public record OwnerInput(@NotNull Long userId, @Size(max=300) String reason) {}
  public record RotateInput(Integer expiresInHours, Boolean singleUse) {}
public record RoleInput(
    @NotNull Long userId,
    @NotBlank @Size(max = 30) String role,
    Long doctorId,
    @Size(max = 300) String reason) {}

public record ExpiryInput(java.time.Instant expiresAt) {}

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
    return workspaces.join(input.code(), clientAddresses.sourceAddress(request));
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
  public void leaveHospital(@PathVariable long id, @RequestParam(required = false) @Size(max=300) String reason) { workspaces.leaveHospital(id, reason); }
  @PostMapping("/departments/{id}/leave")
  public void leaveDepartment(@PathVariable long id, @RequestParam(required = false) @Size(max=300) String reason) { workspaces.leaveDepartment(id, reason); }
  @DeleteMapping("/hospitals/{id}/members/{userId}")
  public void revokeHospital(@PathVariable long id, @PathVariable long userId, @RequestParam(required = false) @Size(max=300) String reason) { workspaces.revokeHospital(id, userId, reason); }
  @DeleteMapping("/departments/{id}/members/{userId}")
  public void revokeDepartment(@PathVariable long id, @PathVariable long userId, @RequestParam(required = false) @Size(max=300) String reason) { workspaces.revokeDepartment(id, userId, reason); }
  @PostMapping("/hospitals/{id}/owners")
  public void grantOwner(@PathVariable long id, @Valid @RequestBody OwnerInput input) {
    workspaces.grantOwner(id, input.userId(), input.reason());
  }
  @PutMapping("/departments/{id}/members/{userId}/expiry")
  public Object membershipExpiry(@PathVariable long id, @PathVariable long userId, @RequestBody ExpiryInput input) {
    return workspaces.setMembershipExpiry(id, userId, input.expiresAt());
  }
  @PostMapping("/departments/{id}/roles")
  public Object grantRole(@PathVariable long id, @Valid @RequestBody RoleInput input) {
    return workspaces.grantRole(id, input.userId(), input.role(), input.doctorId(), input.reason());
  }

  private static Integer hours(RotateInput input) {
    return input == null ? null : input.expiresInHours();
  }

  private static boolean singleUse(RotateInput input) {
    return input != null && Boolean.TRUE.equals(input.singleUse());
  }
}
