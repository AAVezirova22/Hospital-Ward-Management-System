package com.example.hospital.api;

import com.example.hospital.service.CareWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CareWorkflowController {
  public record PublishInput(@NotNull Long version) {}

  private final CareWorkflowService workflows;
  public CareWorkflowController(CareWorkflowService workflows) { this.workflows = workflows; }

  @GetMapping("/care-workflows")
  public Object list() { return workflows.list(); }

  @GetMapping("/care-workflows/assignees")
  public Object assignees() { return workflows.assignees(); }

  @PostMapping("/care-workflows")
  @ResponseStatus(HttpStatus.CREATED)
  public Object create(@Valid @RequestBody CareWorkflowInput in) { return workflows.create(in); }

  @GetMapping("/care-workflows/{id}")
  public Object get(@PathVariable long id) { return workflows.get(id); }

  @PutMapping("/care-workflows/{id}")
  public Object update(@PathVariable long id, @Valid @RequestBody CareWorkflowInput in) { return workflows.update(id, in); }

  @PostMapping("/care-workflows/{id}/preview")
  public Object preview(@PathVariable long id, @RequestBody CareWorkflowService.PreviewInput in) {
    if (in.workflowVersion() != null) return workflows.previewPublished(id, in.workflowVersion(), in);
    return workflows.preview(id, in);
  }

  @PostMapping("/care-workflows/{id}/publish")
  public Object publish(@PathVariable long id, @Valid @RequestBody PublishInput in) { return workflows.publish(id, in.version()); }

  @PostMapping("/care-workflows/{id}/launch")
  @ResponseStatus(HttpStatus.CREATED)
  public Object launch(@PathVariable long id, @Valid @RequestBody CareWorkflowService.LaunchInput in) { return workflows.launch(id, in); }

  @GetMapping("/care-workflow-runs/{id}")
  public Object run(@PathVariable long id) { return workflows.run(id); }

  @GetMapping("/care-workflow-runs")
  public Object runs(@RequestParam(required = false) Long patientId) { return workflows.runs(patientId); }

  @PostMapping("/care-workflow-runs/{id}/approve")
  public Object approveTriggeredRun(@PathVariable long id, @Valid @RequestBody CareWorkflowService.ApprovalInput in) { return workflows.approveTriggeredRun(id, in); }

  @PostMapping("/care-workflow-runs/{id}/cancel")
  public Object cancel(@PathVariable long id, @Valid @RequestBody CareWorkflowService.CancelInput in) { return workflows.cancel(id, in); }

  @GetMapping("/care-tasks")
  public Object tasks() { return workflows.tasks(); }

  @GetMapping("/care-tasks/{id}")
  public Object task(@PathVariable long id) { return workflows.task(id); }

  @PatchMapping("/care-tasks/{id}")
  public Object updateTask(@PathVariable long id, @Valid @RequestBody CareWorkflowService.TaskUpdate in) { return workflows.updateTask(id, in); }

  @GetMapping("/portal/consents")
  public Object consents() { return workflows.consents(); }

  @GetMapping("/portal/consent-options")
  public Object consentOptions() { return workflows.consentOptions(); }

  @PostMapping("/portal/consents")
  @ResponseStatus(HttpStatus.CREATED)
  public Object recordConsent(@Valid @RequestBody CareWorkflowService.ConsentInput in) { return workflows.recordConsent(in); }

  public record ConsentWithdrawal(@jakarta.validation.constraints.AssertTrue boolean confirmed) {}
  @PostMapping("/portal/consents/{id}/withdraw")
  public Object withdrawConsent(@PathVariable long id, @Valid @RequestBody ConsentWithdrawal in) { return workflows.withdrawConsent(id, in.confirmed()); }

  @GetMapping("/portal/care-summaries")
  public Object patientSummaries() { return workflows.patientSummaries(); }
}
