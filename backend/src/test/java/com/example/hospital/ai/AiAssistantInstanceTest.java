package com.example.hospital.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.MessageInput;
import com.example.hospital.domain.AiSession;
import com.example.hospital.domain.AppUser;
import com.example.hospital.repository.AiInteractionRepository;
import com.example.hospital.repository.AiSessionRepository;
import com.example.hospital.security.Actor;
import com.example.hospital.service.AuditService;
import com.example.hospital.service.HospitalService;
import com.example.hospital.service.RateLimitService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiAssistantInstanceTest {
  private static final String SESSION_KEY = "shared-session";

  private final AiModelClient model = mock(AiModelClient.class);
  private final AiToolRegistry tools = mock(AiToolRegistry.class);
  private final AiSessionRepository sessions = mock(AiSessionRepository.class);
  private final AiInteractionRepository interactions = mock(AiInteractionRepository.class);
  private final Actor actor = mock(Actor.class);
  private final HospitalService hospital = mock(HospitalService.class);
  private final AuditService audit = mock(AuditService.class);
  private final RateLimitService rates = mock(RateLimitService.class);
  private final AppUser user = new AppUser();
  private final AiSession session = new AiSession();

  @BeforeEach
  void configureSharedDatabaseState() {
    user.setId(7L);
    session.setId(11L);
    session.setUserId(user.getId());
    session.setSessionKey(SESSION_KEY);

    when(actor.user()).thenReturn(user);
    when(sessions.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(session));
    when(model.identifier()).thenReturn("external-test-model");
    when(tools.executeAgent(any(), any()))
        .thenReturn(new AiToolRegistry.Response("TEXT", "Acknowledged", Map.of(), null, null));
  }

  @Test
  void conversationContextStaysOnTheInstanceThatReceivesStickyRoutedRequests() {
    List<AiModelClient.Context> contexts = new CopyOnWriteArrayList<>();
    when(model.complete(anyString(), any(AiModelClient.Context.class)))
        .thenAnswer(
            call -> {
              contexts.add(call.getArgument(1));
              return respond();
            });

    AiAssistantService firstInstance = newInstance();
    AiAssistantService secondInstance = newInstance();
    firstInstance.message(message("first request"));
    var otherNodeResponse = secondInstance.message(message("request routed elsewhere"));
    firstInstance.message(message("follow-up on the original node"));

    assertThat(otherNodeResponse.sessionId()).isEqualTo(SESSION_KEY);
    assertThat(contexts).hasSize(3);
    assertThat(contexts.get(0).observations()).isEmpty();
    assertThat(contexts.get(1).observations()).isEmpty();
    assertThat(contexts.get(2).observations())
        .containsExactly(
            Map.of("previousUserRequest", "first request", "assistantResponse", "Acknowledged"));
  }

  @Test
  void oneInFlightRequestIsLimitedPerInstance() throws Exception {
    var entered = new CountDownLatch(2);
    var firstEntered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    when(model.complete(anyString(), any(AiModelClient.Context.class)))
        .thenAnswer(
            call -> {
              if (calls.incrementAndGet() == 1) firstEntered.countDown();
              entered.countDown();
              try {
                if (!release.await(5, TimeUnit.SECONDS))
                  throw new IllegalStateException("Timed out waiting for the test to release the model.");
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
              }
              return respond();
            });

    AiAssistantService firstInstance = newInstance();
    AiAssistantService secondInstance = newInstance();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> firstInstance.message(message("first call")));
      assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> firstInstance.message(message("same instance")))
          .isInstanceOf(ApiException.class)
          .satisfies(
              exception -> {
                var apiException = (ApiException) exception;
                assertThat(apiException.getStatus()).isEqualTo(429);
                assertThat(apiException.getCode()).isEqualTo("AI_RATE_LIMIT");
              });

      var second = executor.submit(() -> secondInstance.message(message("other instance")));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
      assertThat(calls).hasValue(2);
    } finally {
      release.countDown();
    }
  }

  private AiAssistantService newInstance() {
    return new AiAssistantService(
        model, tools, sessions, interactions, actor, hospital, audit, rates, 10_000);
  }

  private MessageInput message(String text) {
    return new MessageInput(SESSION_KEY, text, "/app/dashboard", null);
  }

  private AiModelClient.ToolCall respond() {
    return new AiModelClient.ToolCall("respond", Map.of("message", "Acknowledged"));
  }
}
