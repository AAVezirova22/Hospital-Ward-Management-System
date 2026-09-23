package com.example.hospital.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
  private final ClientAddressResolver resolver = new ClientAddressResolver();

  @Test
  void trustedPrivateProxyUsesTheFirstForwardedClientAddress() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("10.20.0.4");
    request.addHeader("X-Forwarded-For", "198.51.100.17");
    request.addHeader("X-Forwarded-For", "203.0.113.23, 10.20.0.3");

    assertThat(resolver.sourceAddress(request)).isEqualTo("198.51.100.17");
  }

  @Test
  void malformedFirstForwardedAddressFallsBackToTrustedPeer() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("10.20.0.4");
    request.addHeader("X-Forwarded-For", "attacker-input, 198.51.100.20");

    assertThat(resolver.sourceAddress(request)).isEqualTo("10.20.0.4");
  }

  @Test
  void ignoresForwardedAddressFromUntrustedPublicPeer() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");
    request.addHeader("X-Forwarded-For", "198.51.100.20");

    assertThat(resolver.sourceAddress(request)).isEqualTo("203.0.113.10");
  }
}
