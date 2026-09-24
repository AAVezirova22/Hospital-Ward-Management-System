package com.example.hospital.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** An IPv4 or IPv6 CIDR block such as {@code 10.0.0.0/8} or {@code fc00::/7}. */
public record IpRange(byte[] network, int prefix) {
  public static IpRange parse(String cidr) {
    String value = cidr == null ? "" : cidr.strip();
    int slash = value.indexOf('/');
    String address = slash < 0 ? value : value.substring(0, slash);
    if (!address.matches("[0-9a-fA-F:.]+") || (address.indexOf(':') < 0 && !address.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")))
      throw new IllegalArgumentException("Not an IP address or CIDR block: " + value);
    byte[] bytes;
    try {
      bytes = InetAddress.getByName(address).getAddress();
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Not an IP address or CIDR block: " + value, e);
    }
    int prefix;
    try {
      prefix = slash < 0 ? bytes.length * 8 : Integer.parseInt(value.substring(slash + 1));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid prefix length in " + value, e);
    }
    if (prefix < 0 || prefix > bytes.length * 8)
      throw new IllegalArgumentException("Invalid prefix length in " + value);
    return new IpRange(bytes, prefix);
  }

  public boolean contains(InetAddress address) {
    byte[] candidate = address.getAddress();
    if (candidate.length != network.length) return false;
    int full = prefix / 8;
    for (int i = 0; i < full; i++) if (candidate[i] != network[i]) return false;
    int rest = prefix % 8;
    if (rest == 0) return true;
    int mask = (0xff << (8 - rest)) & 0xff;
    return (candidate[full] & mask) == (network[full] & mask);
  }
}
