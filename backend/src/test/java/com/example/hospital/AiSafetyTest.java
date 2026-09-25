package com.example.hospital;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.hospital.ai.*;
import com.example.hospital.api.*;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import com.example.hospital.service.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;

class AiSafetyTest {
  Actor actor;
  HospitalService hospital;
  AiActionService actions;
  AiToolRegistry registry;

  @BeforeEach
  void setup() {
    actor = mock(Actor.class);
    hospital = mock(HospitalService.class);
    actions = mock(AiActionService.class);
    var u = new AppUser();
    u.setId(1L);
    u.setUsername("test");
    u.setRole("DOCTOR");
    u.setDoctorId(1L);
    when(actor.user()).thenReturn(u);
    when(actor.doctor()).thenReturn(true);
    registry = new AiToolRegistry(hospital, mock(ReportService.class), actions, actor,
        mock(WorkspaceService.class), new DepartmentTimeService(mock(org.springframework.jdbc.core.JdbcTemplate.class)),
        mock(com.example.hospital.service.AuditService.class));
  }

  @Test
  void forbiddenToolsAndUnknownArgumentsNeverReachBusinessServices() {
    for (var call :
        List.of(
            new AiModelClient.ToolCall("executeSql", Map.of("sql", "DELETE FROM patients")),
            new AiModelClient.ToolCall(
                "getAvailableRooms", Map.of("sql", "SELECT * FROM app_users")),
            new AiModelClient.ToolCall("confirmTransfer", Map.of()))) {
      assertThatThrownBy(() -> registry.execute(call, null)).isInstanceOf(ApiException.class);
    }
    verifyNoInteractions(hospital, actions);
  }

  @Test
  void hiddenPrepareToolIsStillDeniedServerSide() {
    assertThat(registry.definitions().toString())
        .doesNotContain("prepareAdmission", "prepareTransfer", "prepareDischarge");
    assertThatThrownBy(
            () ->
                registry.execute(
                    new AiModelClient.ToolCall(
                        "prepareDischarge", Map.of("patientQuery", "Petrov")),
                    null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(actions);
  }

  @Test
  void invalidNavigationCannotExecuteScriptOrArbitraryUrl() {
    for (String route : List.of("javascript:alert(1)", "https://example.com", "/app/users")) {
      assertThatThrownBy(
              () ->
                  registry.execute(
                      new AiModelClient.ToolCall("navigate", Map.of("route", route)), null))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  @Test
  void malformedNumericToolArgumentsAreInvalidToolCalls() {
    for (var call :
        List.of(
            new AiModelClient.ToolCall("getAvailableRooms", Map.of("minimumFreeBeds", "two")),
            new AiModelClient.ToolCall("getAdmission", Map.of("admissionId", "not-a-number")),
            new AiModelClient.ToolCall(
                "getAdmissions", Map.of("from", "not-a-date", "to", "2026-01-01")),
            new AiModelClient.ToolCall("getAdmissions", Map.of("to", "2026-01-01")))) {
      assertThatThrownBy(() -> registry.execute(call, null))
          .isInstanceOf(ApiException.class)
          .extracting("code")
          .isEqualTo("INVALID_TOOL_CALL");
    }
    verifyNoInteractions(hospital, actions);
  }

  @Test
  void modelFailureIsStructuredAndRateLimitOnlyAffectsAssistant() {
    var model =
        new AiModelClient() {
          public String identifier() {
            return "failure-fixture";
          }

          public ToolCall complete(String m, Context c) {
            throw new IllegalStateException("Provider unavailable");
          }
        };
    var sessions = mock(AiSessionRepository.class);
    when(sessions.save(any()))
        .thenAnswer(
            i -> {
              AiSession s = i.getArgument(0);
              s.setId(1L);
              return s;
            });
    var interactions = mock(AiInteractionRepository.class);
    var rates = mock(RateLimitService.class);
    var attempts = new AtomicInteger();
    when(rates.hit(any(), anyInt(), any(), any(), any()))
        .thenAnswer(
            call -> {
              if (attempts.incrementAndGet() == 1) return new RateLimitService.Budget(1, 0, 60);
              throw new ApiException(429, "AI_RATE_LIMIT", "Wait");
            });
    var service =
        new AiAssistantService(
            model, registry, sessions, interactions, actor, hospital, mock(AuditService.class), rates, 1);
    var response = service.message(new MessageInput(null, "status", "/app/dashboard", null));
    assertThat(response.responseType()).isEqualTo("ERROR");
    assertThat(response.message()).contains("standard hospital screens remain available");
    verify(interactions)
        .save(
            argThat(i -> i.getStatus().equals("FAILED") && i.getModelIdentifier().equals("failure-fixture")));
    assertThatThrownBy(() -> service.message(new MessageInput(null, "status", null, null)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(429));
    verifyNoInteractions(hospital);
  }
}
