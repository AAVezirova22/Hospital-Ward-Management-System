package com.example.hospital.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ClientAddressResolver {
  public String sourceAddress(HttpServletRequest request) {
    String remoteAddress = request.getRemoteAddr();
    String fallback =
        remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.strip();
    InetAddress remoteIp = parseAddress(remoteAddress);
    if (remoteIp == null || !isTrustedProxy(remoteIp)) return fallback;

    List<String> forwarded = forwardedAddresses(request.getHeaders("X-Forwarded-For"));
    // Render places the real client address first; later entries may be supplied by the client.
    InetAddress clientAddress = forwarded.isEmpty() ? null : parseAddress(forwarded.get(0));
    return clientAddress == null ? fallback : clientAddress.getHostAddress();
  }

  private static List<String> forwardedAddresses(Enumeration<String> values) {
    List<String> addresses = new ArrayList<>();
    if (values == null) return addresses;
    for (String value : Collections.list(values)) {
      for (String address : value.split(",", -1)) addresses.add(address.strip());
    }
    return addresses;
  }

  private static InetAddress parseAddress(String value) {
    if (value == null || value.isBlank()) return null;
    String candidate = value.strip();
    if (candidate.indexOf(':') >= 0) {
      if (!candidate.matches("[0-9a-fA-F:.]+")) return null;
    } else if (!candidate.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
      return null;
    }
    try {
      return InetAddress.getByName(candidate);
    } catch (UnknownHostException ignored) {
      return null;
    }
  }

  private static boolean isTrustedProxy(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()) return true;
    if (address instanceof Inet6Address) {
      byte[] bytes = address.getAddress();
      return (bytes[0] & 0xfe) == 0xfc;
    }
    return false;
  }
}
