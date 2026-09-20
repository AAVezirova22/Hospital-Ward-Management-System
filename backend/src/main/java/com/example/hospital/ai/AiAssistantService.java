package com.example.hospital.ai;

import com.example.hospital.api.*;
import com.example.hospital.api.MessageInput;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import com.example.hospital.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.hospital.security.DepartmentContext;

@Service
public class AiAssistantService {
  private final AiModelClient model;
  private final AiToolRegistry tools;
  private final AiSessionRepository sessions;
  private final AiInteractionRepository interactions;
  private final Actor actor;
  private final HospitalService h;
  private final AuditService audit;
  private final RateLimitService rates;
  private final int limit;
  @Autowired private AiSourceService sources;
  @Autowired private ObjectMapper json;
  private record Conversation(long departmentId, Instant expiresAt, List<Map<String, Object>> turns) {}
  private final ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();



  public AiAssistantService(
      AiModelClient m,
      AiToolRegistry t,
      AiSessionRepository s,
      AiInteractionRepository i,
      Actor a,
      HospitalService h,
      AuditService au,
      RateLimitService rates,
      @Value("${app.ai.rate-limit}") int l) {
    model = m;
    tools = t;
    sessions = s;
    interactions = i;
    actor = a;
    this.h = h;
    audit = au;
    this.rates = rates;
    limit = l;
  }

  public AiSession session(String key) {
    var s = sessions.findBySessionKey(key).orElseThrow(ApiException::missing);
    if (!s.userId.equals(actor.user().id))
      throw new AccessDeniedException("Session belongs to another user");
    return s;
  }

  public Object history(String key) {
    var s = session(key);
    return Map.of(
        "sessionId",
        s.sessionKey,
        "interactions",
        interactions.findTop50BySessionIdAndUserIdOrderByStartedAtDesc(key, actor.user().id));
  }

  public void clear(String key) {
    var s = session(key);
    conversations.remove(key);
    s.selectedPatientId = null;
    sessions.save(s); /* Only metadata is persisted; no conversation text to erase. */
  }

  public AiToolRegistry.Response message(MessageInput in) {
    var u = actor.user();
    var window = windows.computeIfAbsent(u.id, k -> new Window());
    synchronized (window) {
      if (window.busy)
        throw new ApiException(
            429, "AI_RATE_LIMIT", "Wait before sending another assistant request.");
      rates.hit(
          "ai:" + u.id,
          limit,
          Duration.ofMinutes(1),
          "AI_RATE_LIMIT",
          "Wait before sending another assistant request.");
      window.busy = true;
    }
    var interaction = new AiInteraction();
    interaction.userId = u.id;
    interaction.startedAt = Instant.now();
    interaction.modelIdentifier = model.identifier();
    interaction.requestType = "MESSAGE";
    interaction.status = "FAILED";
    AiSession s = null;
    try {
      if (in.sessionId() == null || in.sessionId().isBlank()) {
        s = new AiSession();
        s.userId = u.id;
        s.sessionKey = UUID.randomUUID().toString();
        s = sessions.save(s);
      } else s = session(in.sessionId());
      interaction.sessionId = s.sessionKey;
      if (in.selectedPatientId() != null) {
        h.patient(in.selectedPatientId());
        s.selectedPatientId = in.selectedPatientId();
        sessions.save(s);
      } else if (in.route() != null && in.route().startsWith("/app/patients/")) {
        String ref = in.route().substring("/app/patients/".length()).split("[?#]")[0];
        if (!ref.isBlank()) {
          try {
            s.selectedPatientId =
                h.patientByRef(java.net.URLDecoder.decode(ref, java.nio.charset.StandardCharsets.UTF_8)).id;
            sessions.save(s);
          } catch (RuntimeException ignored) {
            // Route may be the directory itself.
          }
        }
      }
      var sourceData = in.sourceIds() == null || in.sourceIds().isEmpty()
          ? List.<Map<String, String>>of() : sources.context(in.sourceIds());
      var connected = in.connectedFiles() == null ? List.<MessageInput.ConnectedFile>of() : in.connectedFiles();
      boolean local = model.identifier().equals("local-command-model");
      AiToolRegistry.Response result;
      if (local && (!sourceData.isEmpty() || !connected.isEmpty())) {
        result = new AiToolRegistry.Response("TEXT",
            "File-based workflow planning requires a configured external AI model. Local command mode only understands the documented commands.",
            Map.of(), null, null);
      } else {
        conversations.values().removeIf(c -> !c.expiresAt().isAfter(Instant.now()));
        var previous = conversations.get(s.sessionKey);
        List<Map<String, Object>> observations = new ArrayList<>();
        if (previous != null && previous.departmentId() == DepartmentContext.id()) observations.addAll(previous.turns());
        result = null;
        for (int step = 0; step < 8; step++) {
          var context = new AiModelClient.Context(u.role,
              in.route() == null ? "/app/dashboard" : in.route(), s.selectedPatientId,
              local ? tools.definitions() : tools.agentDefinitions(), sourceData, connected, observations);
          var call = model.complete(in.message(), context);
          interaction.toolNames = call.name();
          if (call.name().equals("readConnectedFiles")) {
            AiToolRegistry.requireArgument(call, "ids", 700);
            var ids = Arrays.stream(call.arguments().get("ids").split(",")).map(String::strip).distinct().toList();
            var allowed = connected.stream().map(MessageInput.ConnectedFile::id).toList();
            if (ids.isEmpty() || ids.size() > 10 || !allowed.containsAll(ids)) throw new IllegalArgumentException();
            result = new AiToolRegistry.Response("FILE_REQUEST", "Reading relevant files from your connected folder.",
                Map.of("ids", ids), null, null);
            break;
          }
          result = local ? tools.execute(call, s.selectedPatientId) : tools.executeAgent(call, s.selectedPatientId);
          if (local || Set.of("TEXT", "ERROR", "CONFIRMATION_CARD", "WORKFLOW_PROPOSAL", "NAVIGATION_COMMAND").contains(result.responseType())) break;
          String data;
          try { data = json.writeValueAsString(result.data()); }
          catch (Exception e) { throw new IllegalArgumentException(); }
          if (data.length() > 40000) {
            result = new AiToolRegistry.Response("TEXT", "The query returned too much data. Narrow the request by patient, date or department.", Map.of(), null, null);
            break;
          }
          observations.add(Map.of("tool", call.name(), "arguments", call.arguments(), "result", result.data()));
          if (step == 7) result = new AiToolRegistry.Response("TEXT",
              "The task needs more steps. Narrow the request into smaller workflows.", Map.of(), null, null);
        }
        if (!local && result != null && !result.responseType().equals("FILE_REQUEST")) {
          List<Map<String, Object>> turns = new ArrayList<>();
          if (previous != null && previous.departmentId() == DepartmentContext.id()) turns.addAll(previous.turns());
          turns.add(Map.of("previousUserRequest", in.message(), "assistantResponse", result.message()));
          while (turns.size() > 6) turns.removeFirst();
          if (conversations.size() < 1000 || conversations.containsKey(s.sessionKey))
            conversations.put(s.sessionKey, new Conversation(DepartmentContext.id(), Instant.now().plusSeconds(1800), turns));
        }
      }
      interaction.status =
          Set.of("CONFIRMATION_CARD", "WORKFLOW_PROPOSAL").contains(result.responseType()) ? "CONFIRMATION_REQUIRED" : "SUCCESS";
      audit.log("AI_QUERY_EXECUTED", "AiSession", s.id, "AI");
      return new AiToolRegistry.Response(
          result.responseType(), result.message(), result.data(), s.sessionKey, model.identifier());
    } catch (AccessDeniedException e) {
      interaction.status = "REJECTED";
      throw e;
    } catch (ApiException e) {
      interaction.status = "REJECTED";
      throw e;
    } catch (IllegalArgumentException e) {
      interaction.status = "REJECTED";
      throw new ApiException(
          400, "INVALID_TOOL_CALL", "The assistant request could not be interpreted safely.");
    } catch (IllegalStateException e) {
      return new AiToolRegistry.Response(
          "ERROR",
          "The assistant is unavailable. All standard hospital screens remain available.",
          Map.of(),
          s == null ? null : s.sessionKey,
          model.identifier());
    } finally {
      interaction.completedAt = Instant.now();
      interaction.latencyMs =
          Duration.between(interaction.startedAt, interaction.completedAt).toMillis();
      if (interaction.sessionId != null) interactions.save(interaction);
      synchronized (window) {
        window.busy = false;
      }
    }
  }
}
