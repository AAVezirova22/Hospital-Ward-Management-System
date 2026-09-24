package com.example.hospital.ai;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.DepartmentContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Validates a workflow end to end without saving it (#341). The plan runs through the same
 * business operations as a confirmed workflow inside a transaction that is always rolled back, so
 * capacity, uniqueness and permission checks behave exactly as they would on confirmation. No
 * pending action is created and nothing is committed. Audit and notification listeners only run
 * after a commit, so they never fire for a dry run.
 */
@Service
public class WorkflowDryRunService {
  private static final String OCCUPANCY = """
      select r.id, r.room_number, r.bed_count,
             (select count(*) from room_assignments a where a.room_id = r.id and a.released_at is null) as occupied
        from rooms r where r.department_id = ? order by r.id
      """;

  private final AiWorkflowService workflows;
  private final TransactionTemplate transactions;
  private final JdbcTemplate jdbc;

  public WorkflowDryRunService(AiWorkflowService workflows, TransactionTemplate transactions, JdbcTemplate jdbc) {
    this.workflows = workflows;
    this.transactions = transactions;
    this.jdbc = jdbc;
  }

  public Map<String, Object> dryRun(String planText) {
    var result = new LinkedHashMap<String, Object>();
    try {
      var plan = workflows.parse(planText);
      result.put("title", plan.title());
      result.put("steps", plan.steps().size());
      transactions.executeWithoutResult(status -> {
        status.setRollbackOnly();
        long department = DepartmentContext.id();
        var before = occupancy(department);
        workflows.execute(plan);
        var operations = new LinkedHashMap<String, Integer>();
        for (var step : plan.steps()) operations.merge(step.operation(), 1, Integer::sum);
        result.put("operations", operations);
        result.put("capacityChanges", changes(before, occupancy(department)));
      });
      result.put("valid", true);
    } catch (ApiException e) {
      result.put("valid", false);
      result.put("code", e.getCode());
      result.put("message", e.getMessage());
    } catch (org.springframework.security.access.AccessDeniedException e) {
      result.put("valid", false);
      result.put("code", "ACCESS_DENIED");
      result.put("message", "Your role cannot perform every step of this workflow.");
    }
    result.put("committed", false);
    return result;
  }

  private Map<Long, Map<String, Object>> occupancy(long department) {
    var rooms = new LinkedHashMap<Long, Map<String, Object>>();
    for (var row : jdbc.queryForList(OCCUPANCY, department)) rooms.put(((Number) row.get("id")).longValue(), row);
    return rooms;
  }

  private static List<Map<String, Object>> changes(
      Map<Long, Map<String, Object>> before, Map<Long, Map<String, Object>> after) {
    return after.entrySet().stream()
        .filter(e -> before.containsKey(e.getKey()))
        .filter(e -> !before.get(e.getKey()).get("occupied").equals(e.getValue().get("occupied")))
        .map(e -> {
          Map<String, Object> change = new LinkedHashMap<>();
          change.put("roomId", e.getKey());
          change.put("roomNumber", e.getValue().get("room_number"));
          change.put("bedCount", e.getValue().get("bed_count"));
          change.put("occupiedBefore", before.get(e.getKey()).get("occupied"));
          change.put("occupiedAfter", e.getValue().get("occupied"));
          return change;
        })
        .toList();
  }
}
