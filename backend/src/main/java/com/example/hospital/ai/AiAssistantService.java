package com.example.hospital.ai;

import com.example.hospital.api.*;
import com.example.hospital.api.Inputs.MessageInput;
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

@Service
public class AiAssistantService {
  private final AiModelClient model;
  private final AiToolRegistry tools;
  private final AiSessionRepository sessions;
  private final AiInteractionRepository interactions;
  private final Actor actor;
  private final HospitalService h;
  private final AuditService audit;
  private final int limit;
  private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

  private static class Window {
    long start = System.currentTimeMillis();
    int count = 0;
    boolean busy = false;
  }

  public AiAssistantService(
      AiModelClient m,
      AiToolRegistry t,
      AiSessionRepository s,
      AiInteractionRepository i,
      Actor a,
      HospitalService h,
      AuditService au,
      @Value("${app.ai.rate-limit}") int l) {
    model = m;
    tools = t;
    sessions = s;
    interactions = i;
    actor = a;
    this.h = h;
    audit = au;
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
    s.selectedPatientId = null;
    sessions.save(s); /* Only metadata is persisted; no conversation text to erase. */
  }

  public AiToolRegistry.Response message(MessageInput in) {
    var u = actor.user();
    var window = windows.computeIfAbsent(u.id, k -> new Window());
    synchronized (window) {
      if (System.currentTimeMillis() - window.start >= 60000) {
        window.start = System.currentTimeMillis();
        window.count = 0;
      }
      if (window.busy || window.count >= limit)
        throw new ApiException(
            429, "AI_RATE_LIMIT", "Wait before sending another assistant request.");
      window.count++;
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
      }
      var context =
          new AiModelClient.Context(
              u.role,
              in.route() == null ? "/app/dashboard" : in.route(),
              s.selectedPatientId,
              tools.definitions());
      var call = model.complete(in.message(), context);
      interaction.toolNames = call.name();
      var result = tools.execute(call, s.selectedPatientId);
      interaction.status =
          result.responseType().equals("CONFIRMATION_CARD") ? "CONFIRMATION_REQUIRED" : "SUCCESS";
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
