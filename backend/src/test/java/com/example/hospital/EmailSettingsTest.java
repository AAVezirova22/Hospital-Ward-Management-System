package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class EmailSettingsTest extends HospitalSupport {
  private static final HttpServer PROVIDER = provider();

  private static HttpServer provider() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/emails", exchange -> {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int status = body.contains("reject@") ? 403 : body.contains("down@") ? 503 : 200;
        byte[] reply = (status == 200 ? "{\"id\":\"msg_test_1\"}" : "{\"message\":\"secret provider detail\"}")
            .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, reply.length);
        exchange.getResponseBody().write(reply);
        exchange.close();
      });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void email(DynamicPropertyRegistry registry) {
    registry.add("app.email.endpoint", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/emails");
    registry.add("app.email.resend-key", () -> "re_test_key_never_returned");
    registry.add("app.email.from", () -> "Medcore <wards@example.org>");
    registry.add("app.public-url", () -> "http://wards.example.org");
  }

  @Test
  void statusDescribesTheConfigurationWithoutSecrets() throws Exception {
    var status = result(request("admin", "GET", "/api/v1/settings/email", null), 200);
    assertThat(status.get("apiKeyConfigured").asBoolean()).isTrue();
    assertThat(status.get("senderValid").asBoolean()).isTrue();
    assertThat(status.get("testingSender").asBoolean()).isFalse();
    assertThat(status.get("publicUrlValid").asBoolean()).isTrue();
    assertThat(status.get("providerHost").asText()).isEqualTo("127.0.0.1");
    assertThat(status.toString()).doesNotContain("re_test_key_never_returned");
  }

  @Test
  void testSendsAreClassifiedAuditedAndNeverEchoProviderText() throws Exception {
    request("admin", "POST", "/api/v1/settings/email/test", Map.of("recipient", "ok@example.org"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("ACCEPTED_BY_PROVIDER"))
        .andExpect(jsonPath("$.providerMessageId").value("msg_test_1"))
        .andExpect(jsonPath("$.recipientDomain").value("example.org"));
    var rejected = result(request("admin", "POST", "/api/v1/settings/email/test", Map.of("recipient", "reject@example.org")), 200);
    assertThat(rejected.get("outcome").asText()).isEqualTo("PROVIDER_REJECTED");
    assertThat(rejected.get("providerStatus").asInt()).isEqualTo(403);
    assertThat(rejected.toString()).doesNotContain("secret provider detail");
    var down = result(request("admin", "POST", "/api/v1/settings/email/test", Map.of("recipient", "down@example.org")), 200);
    assertThat(down.get("outcome").asText()).isEqualTo("PROVIDER_UNAVAILABLE");

    assertThat(result(request("admin", "GET", "/api/v1/settings/email", null), 200).get("lastTest").get("outcome").asText())
        .isEqualTo("PROVIDER_UNAVAILABLE");
    String metadata = jdbc.queryForObject(
        "select metadata from audit_events where event_type = 'EMAIL_SETTINGS_TESTED' order by id desc limit 1", String.class);
    assertThat(metadata).contains("outcome=PROVIDER_UNAVAILABLE", "recipientDomain=example.org").doesNotContain("down@");
  }

  @Test
  void onlyAdministratorsTestAndRecipientsAreValidated() throws Exception {
    request("staff", "GET", "/api/v1/settings/email", null).andExpect(status().isForbidden());
    request("staff", "POST", "/api/v1/settings/email/test", Map.of("recipient", "ok@example.org"))
        .andExpect(status().isForbidden());
    request("admin", "POST", "/api/v1/settings/email/test", Map.of("recipient", "not-an-address"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RECIPIENT"));
  }
}
