package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Aggregated assistant usage for budgeting (#335): requests, provider-reported tokens and an
 * estimated cost from configured per-million-token prices. Built only from interaction metadata;
 * prompts and replies are never stored or read.
 */
@Service
public class AiUsageReportService {
  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final DepartmentTimeService departmentTime;
  private final BigDecimal inputPerMillion;
  private final BigDecimal outputPerMillion;
  private final String currency;

  public AiUsageReportService(
      JdbcTemplate jdbc,
      Actor actor,
      DepartmentTimeService departmentTime,
      @Value("${app.ai.cost.input-per-million:0}") BigDecimal inputPerMillion,
      @Value("${app.ai.cost.output-per-million:0}") BigDecimal outputPerMillion,
      @Value("${app.ai.cost.currency:USD}") String currency) {
    if (inputPerMillion.signum() < 0 || outputPerMillion.signum() < 0)
      throw new IllegalStateException("AI_COST_INPUT_PER_MILLION and AI_COST_OUTPUT_PER_MILLION must not be negative.");
    this.jdbc = jdbc;
    this.actor = actor;
    this.departmentTime = departmentTime;
    this.inputPerMillion = inputPerMillion;
    this.outputPerMillion = outputPerMillion;
    this.currency = currency;
  }

  public Map<String, Object> report(LocalDate from, LocalDate to) {
    actor.admin();
    ZoneId zone = departmentTime.zoneId();
    LocalDate end = to == null ? LocalDate.now(zone) : to;
    LocalDate start = from == null ? end.minusDays(29) : from;
    if (start.isAfter(end) || start.plusDays(366).isBefore(end))
      throw new ApiException(400, "VALIDATION_ERROR", "Choose a date range of at most 366 days.");
    Timestamp startAt = Timestamp.from(start.atStartOfDay(zone).toInstant());
    Timestamp endAt = Timestamp.from(end.plusDays(1).atStartOfDay(zone).toInstant());
    long department = DepartmentContext.id();
    String where = " from ai_interactions where department_id = ? and started_at >= ? and started_at < ?";
    var totals = jdbc.queryForMap(
        "select count(*) as requests, count(prompt_tokens) as requests_with_usage,"
            + " coalesce(sum(prompt_tokens), 0) as prompt_tokens, coalesce(sum(completion_tokens), 0) as completion_tokens"
            + where, department, startAt, endAt);
    List<Map<String, Object>> byModel = jdbc.queryForList(
        "select coalesce(model_identifier, 'unknown') as model, count(*) as requests,"
            + " coalesce(sum(prompt_tokens), 0) as prompt_tokens, coalesce(sum(completion_tokens), 0) as completion_tokens"
            + where + " group by coalesce(model_identifier, 'unknown') order by requests desc", department, startAt, endAt);
    for (var row : byModel) row.put("estimatedCost", cost(row));
    var summary = new LinkedHashMap<String, Object>(totals);
    summary.put("estimatedCost", cost(totals));
    var result = new LinkedHashMap<String, Object>();
    result.put("from", start);
    result.put("to", end);
    result.put("currency", currency);
    result.put("pricingConfigured", inputPerMillion.signum() > 0 || outputPerMillion.signum() > 0);
    result.put("totals", summary);
    result.put("byModel", byModel);
    return result;
  }

  private BigDecimal cost(Map<String, Object> row) {
    if (inputPerMillion.signum() == 0 && outputPerMillion.signum() == 0) return null;
    BigDecimal prompt = new BigDecimal(((Number) row.get("prompt_tokens")).longValue());
    BigDecimal completion = new BigDecimal(((Number) row.get("completion_tokens")).longValue());
    return prompt.multiply(inputPerMillion).add(completion.multiply(outputPerMillion))
        .divide(BigDecimal.valueOf(1_000_000), 4, RoundingMode.HALF_UP);
  }
}
