package com.example.hospital.api;

import com.example.hospital.ai.WorkflowDryRunService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assistant/workflows")
public class WorkflowDryRunController {
  private final WorkflowDryRunService dryRuns;
  private final ObjectMapper json;

  public WorkflowDryRunController(WorkflowDryRunService dryRuns, ObjectMapper json) {
    this.dryRuns = dryRuns;
    this.json = json;
  }

  /** {@code plan} is the proposal JSON, either as a string (as the assistant sends it) or an object. */
  public record DryRunInput(Object plan) {}

  /** Validates every step and reports the expected effect; never creates a proposal or saves data. */
  @PostMapping("/dry-run")
  public Object dryRun(@RequestBody DryRunInput input) throws JsonProcessingException {
    Object plan = input.plan();
    return dryRuns.dryRun(plan == null ? null : plan instanceof String text ? text : json.writeValueAsString(plan));
  }
}
