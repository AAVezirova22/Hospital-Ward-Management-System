package com.example.hospital.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AiUsageCaptureTest {
  static HttpServer server;
  static final AtomicBoolean reportUsage = new AtomicBoolean(true);

  @BeforeAll
  static void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat", exchange -> {
      exchange.getRequestBody().readAllBytes();
      String usage = reportUsage.get() ? ",\"usage\":{\"prompt_tokens\":1200,\"completion_tokens\":80}" : "";
      byte[] body = ("{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"respond\","
          + "\"arguments\":\"{\\\"message\\\":\\\"hi\\\"}\"}}]}}]" + usage + "}").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  private ExternalAiProviderClient client() {
    return new ExternalAiProviderClient(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "", "test-model", 5, new ObjectMapper());
  }

  @Test
  void providerReportedTokensAreCollectedPerRequestAndAddedUpAcrossSteps() {
    reportUsage.set(true);
    AiUsage.start();
    client().complete("hello", new AiModelClient.Context("ADMIN", "/", null, List.of()));
    client().complete("again", new AiModelClient.Context("ADMIN", "/", null, List.of()));
    assertThat(AiUsage.drain()).isEqualTo(new AiUsage.Tokens(2400, 160));
    assertThat(AiUsage.drain()).as("drain clears the counter").isNull();
  }

  @Test
  void missingUsageLeavesTokensUnknown() {
    reportUsage.set(false);
    AiUsage.start();
    client().complete("hello", new AiModelClient.Context("ADMIN", "/", null, List.of()));
    assertThat(AiUsage.drain()).isNull();
  }
}
