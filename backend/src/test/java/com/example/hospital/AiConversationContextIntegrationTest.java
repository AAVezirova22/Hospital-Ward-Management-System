package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.hospital.ai.AiAssistantService;
import com.example.hospital.ai.AiModelClient;
import com.example.hospital.ai.AiSourceService;
import com.example.hospital.ai.AiToolRegistry;
import com.example.hospital.api.ApiException;
import com.example.hospital.api.MessageInput;
import com.example.hospital.domain.AiSession;
import com.example.hospital.repository.AiInteractionRepository;
import com.example.hospital.repository.AiSessionRepository;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import com.example.hospital.service.AuditService;
import com.example.hospital.service.HospitalService;
import com.example.hospital.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
    properties = {
      "app.seed=true",
      "app.bootstrap-password=IntegrationPassword123!",
      "app.ai.rate-limit=10000",
      "app.ai.context-max-turns=2",
      "app.ai.context-cleanup-interval=1h",
      "server.servlet.session.cookie.secure=false"
    })
class AiConversationContextIntegrationTest extends HospitalSupport {
  @Autowired AiSessionRepository sessions;
  @Autowired AiInteractionRepository interactions;
  @Autowired AiToolRegistry tools;
  @Autowired Actor actor;
  @Autowired HospitalService hospital;
  @Autowired AuditService audit;
  @Autowired RateLimitService rates;
  @Autowired AiSourceService sources;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private ProbeModel model;
  private AiAssistantService firstInstance;
  private AiAssistantService secondInstance;
  private long departmentId;

  @BeforeEach
  void establishRequestScope() {
    departmentId = jdbc.queryForObject("select min(id) from departments", Long.class);
    DepartmentContext.set(new DepartmentContext.Scope(departmentId, "ADMIN", null));
    SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    authenticate("admin");
    model = new ProbeModel();
    firstInstance = newInstance();
    secondInstance = newInstance();
  }

  @AfterEach
  void clearRequestScope() {
    SecurityContextHolder.clearContext();
    DepartmentContext.clear();
  }

  @Test
  void recentContextIsSharedAcrossInstancesBoundedAndClearedWithItsSession() throws Exception {
    String key = createSession();

    firstInstance.message(message(key, "first request"));
    secondInstance.message(message(key, "second request"));
    firstInstance.message(message(key, "third request"));
    secondInstance.message(message(key, "fourth request"));

    assertThat(model.contexts.get(1).observations())
        .containsExactly(
            Map.of("previousUserRequest", "first request", "assistantResponse", "Acknowledged"));
    assertThat(model.contexts.get(3).observations())
        .containsExactly(
            Map.of("previousUserRequest", "second request", "assistantResponse", "Acknowledged"),
            Map.of("previousUserRequest", "third request", "assistantResponse", "Acknowledged"));

    AiSession stored = sessions.findBySessionKey(key).orElseThrow();
    var turns = json.readTree(stored.getConversationContext());
    assertThat(turns).hasSize(2);
    assertThat(turns.get(0).path("previousUserRequest").asText()).isEqualTo("third request");
    assertThat(stored.getConversationExpiresAt()).isAfter(Instant.now());

    secondInstance.clear(key);
    stored = sessions.findBySessionKey(key).orElseThrow();
    assertThat(stored.getConversationContext()).isNull();
    assertThat(stored.getConversationExpiresAt()).isNull();
    assertThat(stored.getSelectedPatientId()).isNull();
  }

  @Test
  void expiredContextIsPurgedAndNeverProvidedToTheModel() {
    String key = createSession();
    AiSession stored = sessions.findBySessionKey(key).orElseThrow();
    stored.setConversationContext(
        "[{\"previousUserRequest\":\"expired\",\"assistantResponse\":\"old\"}]");
    stored.setConversationExpiresAt(Instant.now().minusSeconds(1));
    sessions.saveAndFlush(stored);

    assertThat(sessions.clearExpiredConversationContext(Instant.now())).isOne();
    stored = sessions.findBySessionKey(key).orElseThrow();
    assertThat(stored.getConversationContext()).isNull();

    secondInstance.message(message(key, "new request"));
    assertThat(model.contexts).singleElement().satisfies(context ->
        assertThat(context.observations()).isEmpty());
  }

  @Test
  void contextCannotBeReadByAnotherOwnerOrDepartment() {
    String key = createSession();
    firstInstance.message(message(key, "private request"));

    authenticate("staff");
    assertThatThrownBy(() -> secondInstance.message(message(key, "other owner")))
        .isInstanceOf(AccessDeniedException.class);

    authenticate("admin");
    DepartmentContext.set(new DepartmentContext.Scope(departmentId + 100_000, "ADMIN", null));
    assertThatThrownBy(() -> secondInstance.message(message(key, "other department")))
        .isInstanceOf(ApiException.class)
        .satisfies(exception -> assertThat(((ApiException) exception).getStatus()).isEqualTo(404));
  }

  private String createSession() {
    var session = new AiSession();
    session.setUserId(users.findByUsername("admin").orElseThrow().getId());
    session.setSessionKey(UUID.randomUUID().toString());
    return sessions.saveAndFlush(session).getSessionKey();
  }

  private AiAssistantService newInstance() {
    var service =
        new AiAssistantService(
            model, tools, sessions, interactions, actor, hospital, audit, rates, 10_000);
    ReflectionTestUtils.setField(service, "contextMaxTurns", 2);
    ReflectionTestUtils.setField(service, "sources", sources);
    ReflectionTestUtils.setField(service, "json", json);
    return service;
  }

  private MessageInput message(String sessionId, String text) {
    return new MessageInput(sessionId, text, "/app/dashboard", null, List.of(), List.of());
  }

  private void authenticate(String username) {
    SecurityContextHolder.getContext().setAuthentication(
        new TestingAuthenticationToken(username, "test", "ROLE_ADMIN"));
  }

  private static final class ProbeModel implements AiModelClient {
    private final List<Context> contexts = new CopyOnWriteArrayList<>();

    @Override
    public ToolCall complete(String message, Context context) {
      contexts.add(context);
      return new ToolCall("respond", Map.of("message", "Acknowledged"));
    }

    @Override
    public String identifier() {
      return "external-test-model";
    }
  }
}
