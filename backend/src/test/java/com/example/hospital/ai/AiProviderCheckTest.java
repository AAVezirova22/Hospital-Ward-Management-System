package com.example.hospital.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AiProviderCheckTest {
  static HttpServer server;
  static final AtomicReference<String> behaviour = new AtomicReference<>("ok");
  static final AtomicReference<String> lastRequest = new AtomicReference<>("");

  @BeforeAll
  static void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      lastRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String mode = behaviour.get();
      int status = switch (mode) { case "401" -> 401; case "500" -> 500; default -> 200; };
      String body = switch (mode) {
        case "ok" -> "{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"connectionCheck\",\"arguments\":\"{\\\"value\\\":\\\"ok\\\"}\"}}]}}]}";
        case "text" -> "{\"choices\":[{\"message\":{\"content\":\"Sure, ok!\"}}]}";
        default -> "{\"error\":\"secret provider detail\"}";
      };
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  private static AiProviderCheck check(String mode, String url) {
    return new AiProviderCheck(mode, url, "sk-test-never-returned", "test-model", 5, new ObjectMapper());
  }

  private static String local() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
  }

  @Test
  void classifiesProviderResponses() {
    behaviour.set("ok");
    assertThat(check("external", local()).run().get("outcome")).isEqualTo("COMPATIBLE");
    behaviour.set("text");
    assertThat(check("external", local()).run().get("outcome")).isEqualTo("TOOL_CALLS_UNSUPPORTED");
    behaviour.set("401");
    assertThat(check("external", local()).run()).containsEntry("outcome", "AUTHENTICATION_FAILED").containsEntry("providerStatus", 401);
    behaviour.set("500");
    var result = check("external", local()).run();
    assertThat(result).containsEntry("outcome", "PROVIDER_ERROR");
    assertThat(result.toString()).doesNotContain("secret provider detail", "sk-test-never-returned");
  }

  @Test
  void sendsOnlyTheFixedSyntheticPrompt() {
    behaviour.set("ok");
    check("external", local()).run();
    assertThat(lastRequest.get()).contains("connectionCheck", "Run the connection check.").doesNotContain("patient");
  }

  @Test
  void rejectsInsecureRemoteEndpointsAndNonExternalModes() {
    assertThat(check("external", "http://ai.example.org/v1/chat/completions").run().get("outcome")).isEqualTo("INSECURE_PROTOCOL");
    assertThat(check("local", "").run().get("outcome")).isEqualTo("NOT_EXTERNAL");
    assertThat(check("external", "http://127.0.0.1:1/v1/chat/completions").run().get("outcome")).isEqualTo("UNREACHABLE");
  }
}
