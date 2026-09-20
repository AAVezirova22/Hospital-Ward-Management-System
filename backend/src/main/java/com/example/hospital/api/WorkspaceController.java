package com.example.hospital.api;

import com.example.hospital.security.DepartmentContext;
import com.example.hospital.service.WorkspaceService;
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
  public WorkspaceController(WorkspaceService workspaces) { this.workspaces = workspaces; }
  public record HospitalInput(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=120) String departmentName) {}
  public record DepartmentInput(@NotBlank @Size(max=120) String name) {}
  public record JoinInput(@NotBlank @Size(max=40) String code) {}
  public record OwnerInput(@NotNull Long userId) {}

  @GetMapping
  public Object list() { return Map.of("activeDepartmentId", DepartmentContext.id(), "hospitals", workspaces.list()); }
  @PostMapping("/hospitals") @ResponseStatus(HttpStatus.CREATED)
  public Object create(@Valid @RequestBody HospitalInput input) {
    return workspaces.createHospital(input.name(), input.departmentName());
  }
  @PostMapping("/hospitals/{id}/departments") @ResponseStatus(HttpStatus.CREATED)
  public Object department(@PathVariable long id, @Valid @RequestBody DepartmentInput input) {
    return Map.of("hospitalId", id, "departmentId", workspaces.createDepartment(id, input.name()));
  }
  @PostMapping("/join")
  public Object join(@Valid @RequestBody JoinInput input, HttpServletRequest request) {
    return workspaces.join(input.code(), request.getRemoteAddr());
  }
  @PostMapping("/hospitals/{id}/code")
  public Object hospitalCode(@PathVariable long id) { return Map.of("code", workspaces.rotate(true, id)); }
  @PostMapping("/departments/{id}/code")
  public Object departmentCode(@PathVariable long id) { return Map.of("code", workspaces.rotate(false, id)); }
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
}
