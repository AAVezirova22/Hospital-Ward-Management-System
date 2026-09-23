package com.example.hospital.service;

import com.example.hospital.security.DepartmentContext;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.*;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Invalidation only: all patient data is re-fetched through authorized REST endpoints. */
@Service
public class OperationsStream {
  public record Changed(long departmentId) {}
  private final ConcurrentHashMap<Long, Set<SseEmitter>> clients = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    var thread = new Thread(r, "operations-stream"); thread.setDaemon(true); return thread;
  });
  public SseEmitter connect() {
    long departmentId = DepartmentContext.id();
    var emitter = new SseEmitter(25000L);
    var group = clients.computeIfAbsent(departmentId, id -> ConcurrentHashMap.newKeySet());
    group.add(emitter);
    var expiry = scheduler.schedule(() -> remove(departmentId, emitter), 20, TimeUnit.SECONDS);
    Runnable done = () -> { remove(departmentId, emitter); expiry.cancel(false); };
    emitter.onCompletion(done); emitter.onTimeout(done); emitter.onError(e -> done.run());
    send(departmentId, emitter, "ready");
    return emitter;
  }
  @TransactionalEventListener
  public void committed(Changed event) {
    var group = clients.get(event.departmentId());
    if (group == null) return;
    group.forEach(emitter -> send(event.departmentId(), emitter, "changed"));
  }
  private void send(long departmentId, SseEmitter emitter, String name) {
    try { emitter.send(SseEmitter.event().name(name).data("refresh").reconnectTime(1500)); }
    catch (Exception e) { remove(departmentId, emitter); emitter.complete(); }
  }
  private void remove(long departmentId, SseEmitter emitter) {
    var group = clients.get(departmentId);
    if (group == null) return;
    group.remove(emitter);
    if (group.isEmpty()) clients.remove(departmentId, group);
  }
  /** Ends open streams as shutdown begins so graceful shutdown does not wait on idle clients. */
  @EventListener(ContextClosedEvent.class)
  public void shuttingDown() {
    clients.values().forEach(group -> group.forEach(SseEmitter::complete));
    clients.clear();
  }
  @PreDestroy public void close() {
    shuttingDown();
    scheduler.shutdownNow();
  }
}
