package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.hospital.service.SlowQueryDiagnostics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(OutputCaptureExtension.class)
class SlowQueryDiagnosticsTest extends HospitalSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired MeterRegistry meters;

  @Test
  void labelsRedactLiteralsAndCollapseLists() {
    assertThat(
            SlowQueryDiagnostics.label(
                "select * from patients\n where last_name = 'O''Brien' and id in (?, ?, ?) and age > 42"))
        .isEqualTo("select * from patients where last_name = ? and id in (?...) and age > ?");
    assertThat(SlowQueryDiagnostics.label("update rooms set v2 = ? where id = 7"))
        .isEqualTo("update rooms set v2 = ? where id = ?");
  }

  @Test
  void slowStatementsAreLoggedWithoutValuesAndTimed(CapturedOutput output) {
    jdbc.queryForObject("select 'Secret Patient' || pg_sleep(0.6)::text", String.class);

    assertThat(output.getOut())
        .contains("Slow select statement took")
        .contains("select ? || pg_sleep(?)::text")
        .doesNotContain("Secret Patient");
    assertThat(meters.find("hospital.db.statements").tag("operation", "select").timer())
        .isNotNull()
        .satisfies(t -> assertThat(t.count()).isPositive());
  }
}
