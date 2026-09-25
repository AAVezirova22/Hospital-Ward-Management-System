package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.ai.AiModelClient;
import com.example.hospital.ai.LocalModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(AiRetryTest.FlakyProvider.class)
class AiRetryTest extends HospitalSupport {
  static final AtomicInteger failures = new AtomicInteger();

  @TestConfiguration
  static class FlakyProvider {
    @Bean
    @Primary
    AiModelClient flakyModel() {
      var local = new LocalModelClient();
      return new AiModelClient() {
        public ToolCall complete(String message, Context context) {
          if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0)
            throw new IllegalStateException("Assistant provider unavailable or invalid response.");
          return local.complete(message, context);
        }

        public String identifier() {
          return "flaky-test";
        }
      };
    }
  }

  private JsonNode send(String message, String sessionId, String retryToken) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("message", message);
    if (sessionId != null) body.put("sessionId", sessionId);
    if (retryToken != null) body.put("retryToken", retryToken);
    return result(request("admin", "POST", "/api/v1/assistant/messages", body), 200);
  }

  @Test
  void aTransientFailureOffersOneTimeRetryThatSucceeds() throws Exception {
    failures.set(1);
    var failed = send("status", null, null);
    assertThat(failed.get("responseType").asText()).isEqualTo("ERROR");
    assertThat(failed.get("data").get("retryable").asBoolean()).isTrue();
    assertThat(failed.get("data").get("attemptsLeft").asInt()).isEqualTo(2);
    String token = failed.get("data").get("retryToken").asText();
    String session = failed.get("sessionId").asText();

    var retried = send("status", session, token);
    assertThat(retried.get("responseType").asText()).isNotEqualTo("ERROR");

    var body = new HashMap<String, Object>(Map.of("message", "status", "sessionId", session, "retryToken", token));
    request("admin", "POST", "/api/v1/assistant/messages", body)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RETRY_NOT_ALLOWED"));
  }

  @Test
  void tokensOnlyRetryTheSameMessageAndRetriesAreBounded() throws Exception {
    failures.set(1);
    var failed = send("status", null, null);
    var body = new HashMap<String, Object>(Map.of(
        "message", "discharge everyone", "sessionId", failed.get("sessionId").asText(),
        "retryToken", failed.get("data").get("retryToken").asText()));
    request("admin", "POST", "/api/v1/assistant/messages", body)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RETRY_NOT_ALLOWED"));

    failures.set(3);
    var first = send("status", null, null);
    String session = first.get("sessionId").asText();
    var second = send("status", session, first.get("data").get("retryToken").asText());
    assertThat(second.get("data").get("attemptsLeft").asInt()).isOne();
    var third = send("status", session, second.get("data").get("retryToken").asText());
    assertThat(third.get("responseType").asText()).isEqualTo("ERROR");
    assertThat(third.get("data").get("retryable").asBoolean()).isFalse();
    assertThat(third.get("data").has("retryToken")).isFalse();
    failures.set(0);
  }
}
