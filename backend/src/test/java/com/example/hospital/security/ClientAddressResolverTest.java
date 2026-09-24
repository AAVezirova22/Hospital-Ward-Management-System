package com.example.hospital.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
  private final ClientAddressResolver resolver = new ClientAddressResolver();

  private static MockHttpServletRequest from(String peer, String... forwardedFor) {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    for (String value : forwardedFor) request.addHeader("X-Forwarded-For", value);
    return request;
  }

  @Test
  void trustedProxyChainResolvesToTheFirstUntrustedHopFromTheRight() {
    var request = from("10.20.0.4", "198.51.100.17", "203.0.113.23, 10.20.0.3");

    assertThat(resolver.sourceAddress(request)).isEqualTo("203.0.113.23");
  }

  @Test
  void clientSuppliedEntriesLeftOfTheRealClientAreIgnored() {
    var request = from("10.20.0.4", "127.0.0.1, 192.0.2.99, 198.51.100.20");

    assertThat(resolver.sourceAddress(request)).isEqualTo("198.51.100.20");
  }

  @Test
  void malformedEntriesStopTheWalkAtTheLastTrustworthyHop() {
    assertThat(resolver.sourceAddress(from("10.20.0.4", "attacker-input, 198.51.100.20")))
        .isEqualTo("198.51.100.20");
    assertThat(resolver.sourceAddress(from("10.20.0.4", "198.51.100.20, attacker-input")))
        .isEqualTo("10.20.0.4");
  }

  @Test
  void ignoresForwardedAddressFromUntrustedPublicPeer() {
    assertThat(resolver.sourceAddress(from("203.0.113.10", "198.51.100.20"))).isEqualTo("203.0.113.10");
  }

  @Test
  void explicitProxyRangesReplaceTheDefaults() {
    var narrow = new ClientAddressResolver("10.20.0.0/24", "");

    assertThat(narrow.sourceAddress(from("10.20.0.4", "198.51.100.20"))).isEqualTo("198.51.100.20");
    assertThat(narrow.sourceAddress(from("192.168.1.9", "198.51.100.20"))).isEqualTo("192.168.1.9");
    assertThat(narrow.sourceAddress(from("10.20.0.4", "198.51.100.20, 10.20.1.7")))
        .as("a hop outside the listed ranges is treated as the client")
        .isEqualTo("10.20.1.7");
  }

  @Test
  void noneDisablesForwardingHeaders() {
    var direct = new ClientAddressResolver("none", "True-Client-IP");
    var request = from("10.20.0.4", "198.51.100.20");
    request.addHeader("True-Client-IP", "198.51.100.21");

    assertThat(direct.sourceAddress(request)).isEqualTo("10.20.0.4");
  }

  @Test
  void edgeClientHeaderIsUsedOnlyFromTrustedPeersAndOnlyWhenValid() {
    var edge = new ClientAddressResolver(ClientAddressResolver.DEFAULT_TRUSTED_PROXIES, "True-Client-IP");

    var trusted = from("10.20.0.4", "203.0.113.1, 172.71.195.123, 10.226.90.65");
    trusted.addHeader("True-Client-IP", "81.97.145.24");
    assertThat(edge.sourceAddress(trusted)).isEqualTo("81.97.145.24");

    var untrusted = from("203.0.113.10");
    untrusted.addHeader("True-Client-IP", "81.97.145.24");
    assertThat(edge.sourceAddress(untrusted)).isEqualTo("203.0.113.10");

    var malformed = from("10.20.0.4", "198.51.100.20");
    malformed.addHeader("True-Client-IP", "81.97.145.24, 1.2.3.4");
    assertThat(edge.sourceAddress(malformed)).isEqualTo("198.51.100.20");
  }

  @Test
  void ipv6ProxiesAndClientsAreSupported() {
    var request = from("fd00::5", "2001:db8::1, fd00::4");

    assertThat(resolver.sourceAddress(request)).isEqualTo("2001:db8:0:0:0:0:0:1");
  }

  @Test
  void invalidRangesFailStartupWithTheVariableName() {
    assertThatThrownBy(() -> new ClientAddressResolver("10.0.0.0/33", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TRUSTED_PROXIES");
    assertThatThrownBy(() -> new ClientAddressResolver("proxy.internal", ""))
        .isInstanceOf(IllegalStateException.class);
  }
}
