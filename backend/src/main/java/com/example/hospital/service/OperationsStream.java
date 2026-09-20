package com.example.hospital.service;

import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Invalidation only: all patient data is re-fetched through authorized REST endpoints. */
@Service
public class OperationsStream {
  public record Changed() {}
  private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    var thread = new Thread(r, "operations-stream"); thread.setDaemon(true); return thread;
  });
  public SseEmitter connect() {
    var emitter = new SseEmitter(25000L);
    clients.add(emitter);
    var expiry = scheduler.schedule(() -> { clients.remove(emitter); emitter.complete(); }, 20, TimeUnit.SECONDS);
    Runnable remove = () -> { clients.remove(emitter); expiry.cancel(false); };
    emitter.onCompletion(remove); emitter.onTimeout(remove); emitter.onError(e -> remove.run());
    send(emitter, "ready");
    return emitter;
  }
  @TransactionalEventListener
  public void committed(Changed event) { clients.forEach(e -> send(e, "changed")); }
  private void send(SseEmitter emitter, String name) {
    try { emitter.send(SseEmitter.event().name(name).data("refresh").reconnectTime(1500)); }
    catch (Exception e) { clients.remove(emitter); emitter.complete(); }
  }
  @PreDestroy public void close() { clients.forEach(SseEmitter::complete); clients.clear(); scheduler.shutdownNow(); }
}
