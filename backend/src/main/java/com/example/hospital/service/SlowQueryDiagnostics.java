package com.example.hospital.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Times every JDBC statement. Statements slower than the threshold are logged with a redacted
 * label: bound parameters are never read, and literals in the SQL text are replaced by {@code ?},
 * so no patient data reaches the log. All statements feed the {@code hospital.db.statements} timer.
 */
@Component
public class SlowQueryDiagnostics implements BeanPostProcessor {
  private static final Logger log = LoggerFactory.getLogger("hospital.slow-query");
  private static final Pattern STRING = Pattern.compile("'(?:[^']|'')*'");
  private static final Pattern NUMBER = Pattern.compile("(?<![\\w$])-?\\d+(?:\\.\\d+)?");
  private static final Pattern IN_LIST = Pattern.compile("\\(\\s*\\?(?:\\s*,\\s*\\?)+\\s*\\)");
  private static final Pattern SPACE = Pattern.compile("\\s+");
  private static final int MAX_LABEL = 240;

  private final long thresholdMs;
  private final ObjectProvider<MeterRegistry> meters;

  public SlowQueryDiagnostics(
      @Value("${app.diagnostics.slow-query-ms:500}") long thresholdMs,
      ObjectProvider<MeterRegistry> meters) {
    this.thresholdMs = thresholdMs;
    this.meters = meters;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String name) {
    if (bean instanceof DataSource ds && !(Proxy.isProxyClass(bean.getClass())))
      return proxy(DataSource.class, ds, new DataSourceHandler(ds));
    return bean;
  }

  /** Reduces SQL to a safe label: literals become {@code ?}, whitespace is collapsed. */
  public static String label(String sql) {
    if (sql == null) return "unknown";
    String s = STRING.matcher(sql).replaceAll("?");
    s = NUMBER.matcher(s).replaceAll("?");
    s = IN_LIST.matcher(s).replaceAll("(?...)");
    s = SPACE.matcher(s).replaceAll(" ").trim();
    return s.length() > MAX_LABEL ? s.substring(0, MAX_LABEL) + "..." : s;
  }

  static String operation(String sql) {
    if (sql == null) return "other";
    String first = sql.stripLeading().split("\\s", 2)[0].toLowerCase(Locale.ROOT);
    return switch (first) {
      case "select", "insert", "update", "delete", "with" -> first;
      default -> "other";
    };
  }

  void record(String sql, long nanos, boolean failed) {
    String op = operation(sql);
    MeterRegistry registry = meters.getIfAvailable();
    if (registry != null)
      Timer.builder("hospital.db.statements")
          .description("JDBC statement execution time")
          .tag("operation", op)
          .tag("outcome", failed ? "error" : "success")
          .register(registry)
          .record(Duration.ofNanos(nanos));
    long ms = nanos / 1_000_000;
    if (thresholdMs > 0 && ms >= thresholdMs)
      log.warn("Slow {} statement took {} ms: {}", op, ms, label(sql));
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, Object target, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object call(Object target, Method m, Object[] args) throws Throwable {
    try {
      return m.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /** Lets {@code unwrap}/{@code isWrapperFor} reach the pool (e.g. for Hikari metrics). */
  private static Object wrapper(Object target, Method m, Object[] args) throws Throwable {
    Class<?> type = (Class<?>) args[0];
    if ("isWrapperFor".equals(m.getName()))
      return type.isInstance(target) || (boolean) call(target, m, args);
    return type.isInstance(target) ? target : call(target, m, args);
  }

  private final class DataSourceHandler implements InvocationHandler {
    private final DataSource target;

    DataSourceHandler(DataSource target) {
      this.target = target;
    }

    public Object invoke(Object p, Method m, Object[] args) throws Throwable {
      if (m.getName().equals("unwrap") || m.getName().equals("isWrapperFor"))
        return wrapper(target, m, args);
      Object result = call(target, m, args);
      return result instanceof Connection c ? proxy(Connection.class, c, new ConnectionHandler(c)) : result;
    }
  }

  private final class ConnectionHandler implements InvocationHandler {
    private final Connection target;

    ConnectionHandler(Connection target) {
      this.target = target;
    }

    public Object invoke(Object p, Method m, Object[] args) throws Throwable {
      if (m.getName().equals("unwrap") || m.getName().equals("isWrapperFor"))
        return wrapper(target, m, args);
      Object result = call(target, m, args);
      String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
      if (result instanceof CallableStatement cs)
        return proxy(CallableStatement.class, cs, new StatementHandler(cs, sql));
      if (result instanceof PreparedStatement ps)
        return proxy(PreparedStatement.class, ps, new StatementHandler(ps, sql));
      if (result instanceof Statement st)
        return proxy(Statement.class, st, new StatementHandler(st, null));
      return result;
    }
  }

  private final class StatementHandler implements InvocationHandler {
    private final Statement target;
    private final String prepared;

    StatementHandler(Statement target, String prepared) {
      this.target = target;
      this.prepared = prepared;
    }

    public Object invoke(Object p, Method m, Object[] args) throws Throwable {
      if (m.getName().equals("unwrap") || m.getName().equals("isWrapperFor"))
        return wrapper(target, m, args);
      if (!m.getName().startsWith("execute")) return call(target, m, args);
      String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : prepared;
      long start = System.nanoTime();
      boolean failed = true;
      try {
        Object result = call(target, m, args);
        failed = false;
        return result;
      } finally {
        record(sql, System.nanoTime() - start, failed);
      }
    }
  }
}
