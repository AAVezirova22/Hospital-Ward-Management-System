package com.example.hospital.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiProviderHealthTest {
  private static AiProviderHealth health(String mode) {
    return new AiProviderHealth(mode, false, mock(AiProviderCheck.class));
  }

  @Test
  void requestFailuresDegradeThenMarkUnavailableAndSuccessRecovers() {
    var health = health("external");
    assertThat(health.snapshot().status()).isEqualTo("UNKNOWN");
    health.record("TRAFFIC", "TIMEOUT");
    assertThat(health.snapshot().status()).isEqualTo("DEGRADED");
    assertThat(health.snapshot().category()).isEqualTo("TIMEOUT");
    health.record("TRAFFIC", "PROVIDER_ERROR");
    health.record("TRAFFIC", "PROVIDER_ERROR");
    assertThat(health.snapshot().status()).isEqualTo("UNAVAILABLE");
    assertThat(health.snapshot().consecutiveFailures()).isEqualTo(3);
    health.record("TRAFFIC", "OK");
    assertThat(health.snapshot())
        .satisfies(s -> {
          assertThat(s.status()).isEqualTo("HEALTHY");
          assertThat(s.consecutiveFailures()).isZero();
          assertThat(s.lastFailureAt()).isNotNull();
          assertThat(s.lastSuccessAt()).isNotNull();
        });
  }

  @Test
  void failedTestsAreDefinitiveAndLocalModeIsNotTracked() {
    var health = health("external");
    health.record("TEST", "AUTHENTICATION_FAILED");
    assertThat(health.snapshot().status()).isEqualTo("UNAVAILABLE");
    assertThat(health.snapshot().source()).isEqualTo("TEST");

    var local = health("local");
    local.record("TRAFFIC", "TIMEOUT");
    assertThat(local.snapshot().status()).isEqualTo("NOT_EXTERNAL");
  }

  @Test
  void providerClientReportsCategoriesWithoutProviderText() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat", exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] body = "{\"error\":\"secret provider detail\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(401, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      var health = health("external");
      var client = new ExternalAiProviderClient("http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
          "", "m", 5, new ObjectMapper(), health);
      try {
        client.complete("hi", new AiModelClient.Context("ADMIN", "/", null, List.of()));
      } catch (IllegalStateException expected) {
        // The assistant turns this into its normal unavailable response.
      }
      assertThat(health.snapshot().category()).isEqualTo("AUTHENTICATION_FAILED");
      assertThat(health.snapshot().toString()).doesNotContain("secret provider detail");

      var unreachable = new ExternalAiProviderClient("http://127.0.0.1:1/chat", "", "m", 2, new ObjectMapper(), health);
      try {
        unreachable.complete("hi", new AiModelClient.Context("ADMIN", "/", null, List.of()));
      } catch (IllegalStateException expected) {
        // As above.
      }
      assertThat(health.snapshot().category()).isEqualTo("UNREACHABLE");
    } finally {
      server.stop(0);
    }
  }
}
