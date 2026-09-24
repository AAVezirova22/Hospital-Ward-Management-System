package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.ai.cost.input-per-million=3", "app.ai.cost.output-per-million=15"})
class AiUsageReportTest extends HospitalSupport {
  private void interaction(LocalDate day, String model, Long prompt, Long completion) {
    long adminId = users.findByUsername("admin").orElseThrow().getId();
    jdbc.update(
        "insert into ai_interactions(department_id, user_id, session_id, model_identifier, status, started_at,"
            + " prompt_tokens, completion_tokens) values (1, ?, 'usage-test', ?, 'SUCCESS', ?, ?, ?)",
        adminId, model, Timestamp.from(day.atTime(12, 0).toInstant(ZoneOffset.UTC)), prompt, completion);
  }

  @Test
  void aggregatesTokensAndEstimatesCostWithoutConversationContent() throws Exception {
    LocalDate day = LocalDate.of(2001 + ThreadLocalRandom.current().nextInt(18), 3, 3);
    interaction(day, "model-a", 1_000_000L, 100_000L);
    interaction(day, "model-a", 500_000L, 0L);
    interaction(day, "model-b", null, null);

    var report = result(request("admin", "GET", "/api/v1/reports/ai-usage?from=" + day + "&to=" + day, null), 200);
    var totals = report.get("totals");
    assertThat(totals.get("requests").asLong()).isEqualTo(3);
    assertThat(totals.get("requests_with_usage").asLong()).isEqualTo(2);
    assertThat(totals.get("prompt_tokens").asLong()).isEqualTo(1_500_000);
    assertThat(totals.get("completion_tokens").asLong()).isEqualTo(100_000);
    assertThat(totals.get("estimatedCost").decimalValue()).isEqualByComparingTo("6.0");
    assertThat(report.get("pricingConfigured").asBoolean()).isTrue();
    assertThat(report.get("byModel").get(0).get("model").asText()).isEqualTo("model-a");
    assertThat(report.get("byModel").get(0).get("estimatedCost").decimalValue()).isEqualByComparingTo("6.0");
  }

  @Test
  void onlyAdministratorsSeeUsageAndRangesAreBounded() throws Exception {
    request("staff", "GET", "/api/v1/reports/ai-usage", null).andExpect(status().isForbidden());
    request("admin", "GET", "/api/v1/reports/ai-usage?from=2020-01-01&to=2022-01-01", null)
        .andExpect(status().isBadRequest());
  }
}
