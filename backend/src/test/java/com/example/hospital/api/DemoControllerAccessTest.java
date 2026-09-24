package com.example.hospital.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.hospital.domain.AppUser;
import com.example.hospital.repository.AppUserRepository;
import com.example.hospital.service.DemoService;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class DemoControllerAccessTest {
  private final DemoService demo = mock(DemoService.class);
  private final AppUserRepository users = mock(AppUserRepository.class);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsProxyAddressWhenPublicLoginIsDisabledEvenWithForwardedLoopbackHeader() {
    var controller = new DemoController(demo, users, "", "demo", false);
    var request = proxyRequest();
    request.addHeader("X-Forwarded-For", "127.0.0.1");

    assertThatThrownBy(
            () ->
                controller.login(
                    new DemoController.DemoLogin("ADMIN"),
                    request,
                    new MockHttpServletResponse()))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("DEMO_TOKEN_REQUIRED");
  }

  @Test
  void configuredTokenRemainsRequiredWhenPublicLoginIsEnabled() {
    var controller = new DemoController(demo, users, "configured-token", "demo", true);
    var missingTokenRequest = proxyRequest();

    assertThatThrownBy(
            () ->
                controller.login(
                    new DemoController.DemoLogin("ADMIN"),
                    missingTokenRequest,
                    new MockHttpServletResponse()))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("DEMO_TOKEN_REQUIRED");

    var invalidTokenRequest = proxyRequest();
    invalidTokenRequest.addHeader("X-Demo-Token", "invalid-token");
    assertThatThrownBy(
            () ->
                controller.login(
                    new DemoController.DemoLogin("ADMIN"),
                    invalidTokenRequest,
                    new MockHttpServletResponse()))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("DEMO_TOKEN_REQUIRED");
  }

  @Test
  void publicLoginUsesBackendModeAndPreservesTheSelectedRoleSession() {
    var doctor = account("doctor", "DOCTOR");
    var accounts =
        Map.of(
            "admin", account("admin", "ADMIN"),
            "doctor", doctor,
            "staff", account("staff", "MEDICAL_STAFF"));
    when(users.findByUsername(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(accounts.get(invocation.getArgument(0))));
    var controller = new DemoController(demo, users, "", "demo", true);
    var request = proxyRequest();
    request.addHeader("X-Forwarded-For", "127.0.0.1");

    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>)
            controller.login(
                new DemoController.DemoLogin("DOCTOR"),
                request,
                new MockHttpServletResponse());

    assertThat(result).containsEntry("role", "DOCTOR");
    assertThat(request.getSession(false).getAttribute("accountId")).isEqualTo(doctor.getId());
    verify(users).save(doctor);
  }

  private static MockHttpServletRequest proxyRequest() {
    var request = new MockHttpServletRequest("POST", "/api/v1/demo/login");
    request.setRemoteAddr("198.51.100.17");
    return request;
  }

  private static AppUser account(String username, String role) {
    var user = new AppUser();
    user.setId(ThreadLocalRandom.current().nextLong());
    user.setUsername(username);
    user.setRole(role);
    user.setEnabled(true);
    user.setSessionStamp("session-stamp-" + user.getId());
    return user;
  }
}
