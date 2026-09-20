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
    u.id = 1L;
    u.username = "test";
    u.role = "DOCTOR";
    u.doctorId = 1L;
    when(actor.user()).thenReturn(u);
    when(actor.doctor()).thenReturn(true);
    registry = new AiToolRegistry(hospital, actions, actor, mock(WorkspaceService.class));
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
              s.id = 1L;
              return s;
            });
    var interactions = mock(AiInteractionRepository.class);
    var service =
        new AiAssistantService(
            model, registry, sessions, interactions, actor, hospital, mock(AuditService.class), 1);
    var response = service.message(new MessageInput(null, "status", "/app/dashboard", null));
    assertThat(response.responseType()).isEqualTo("ERROR");
    assertThat(response.message()).contains("standard hospital screens remain available");
    verify(interactions)
        .save(
            argThat(i -> i.status.equals("FAILED") && i.modelIdentifier.equals("failure-fixture")));
    assertThatThrownBy(() -> service.message(new MessageInput(null, "status", null, null)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status).isEqualTo(429));
    verifyNoInteractions(hospital);
  }
}
