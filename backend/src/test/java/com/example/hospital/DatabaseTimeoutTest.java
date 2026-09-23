package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.hospital.api.Errors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

class DatabaseTimeoutTest extends HospitalSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;
  @Autowired Errors errors;

  @Test
  void connectionsCarryTheConfiguredStatementTimeout() {
    assertThat(jdbc.queryForObject("show statement_timeout", String.class)).isEqualTo("15s");
  }

  @Test
  void exceededStatementTimeoutIsReportedAsRetryableTimeout() throws Exception {
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    s -> {
                      jdbc.execute("set local statement_timeout = 50");
                      jdbc.execute("select pg_sleep(1)");
                    }))
        .isInstanceOf(QueryTimeoutException.class);

    var handler = Errors.class.getDeclaredMethod("timeout", Exception.class, jakarta.servlet.http.HttpServletRequest.class);
    handler.setAccessible(true);
    var response =
        (org.springframework.http.ResponseEntity<?>)
            handler.invoke(errors, new QueryTimeoutException("x"), new MockHttpServletRequest("GET", "/api/v1/reports"));
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
    assertThat(response.getBody().toString()).contains("DATABASE_TIMEOUT");
  }
}
