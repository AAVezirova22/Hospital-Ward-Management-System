package com.example.hospital.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class IpRangeTest {
  private static boolean in(String cidr, String address) throws Exception {
    return IpRange.parse(cidr).contains(InetAddress.getByName(address));
  }

  @Test
  void matchesPrefixesOnByteAndBitBoundaries() throws Exception {
    assertThat(in("10.0.0.0/8", "10.255.1.2")).isTrue();
    assertThat(in("10.0.0.0/8", "11.0.0.1")).isFalse();
    assertThat(in("172.16.0.0/12", "172.31.255.255")).isTrue();
    assertThat(in("172.16.0.0/12", "172.32.0.1")).isFalse();
    assertThat(in("198.51.100.7", "198.51.100.7")).isTrue();
    assertThat(in("198.51.100.7", "198.51.100.8")).isFalse();
    assertThat(in("0.0.0.0/0", "203.0.113.9")).isTrue();
  }

  @Test
  void keepsAddressFamiliesApart() throws Exception {
    assertThat(in("fc00::/7", "fd12:3456::1")).isTrue();
    assertThat(in("fc00::/7", "fe80::1")).isFalse();
    assertThat(in("10.0.0.0/8", "::1")).isFalse();
  }

  @Test
  void rejectsHostnamesAndBadPrefixes() {
    assertThatThrownBy(() -> IpRange.parse("proxy.example")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IpRange.parse("10.0.0.0/40")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IpRange.parse("10.0.0.0/x")).isInstanceOf(IllegalArgumentException.class);
  }
}
