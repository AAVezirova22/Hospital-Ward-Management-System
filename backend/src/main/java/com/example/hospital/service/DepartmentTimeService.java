package com.example.hospital.service;

import com.example.hospital.security.DepartmentContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DepartmentTimeService {
  private final JdbcTemplate jdbc;

  public DepartmentTimeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public ZoneId zoneId() {
    long departmentId = DepartmentContext.id();
    if (departmentId <= 0) return ZoneId.of("UTC");
    List<String> values = jdbc.queryForList(
        "select time_zone from departments where id=?", String.class, departmentId);
    if (values == null || values.isEmpty()) return ZoneId.of("UTC");
    return ZoneId.of(values.getFirst());
  }

  public String timeZone() {
    return zoneId().getId();
  }

  public LocalDate today() {
    return LocalDate.now(zoneId());
  }
}
