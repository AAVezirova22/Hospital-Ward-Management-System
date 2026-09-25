package com.example.hospital.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client address used for rate limits and login backoff. Forwarding headers are only
 * honoured when the direct peer is inside {@code TRUSTED_PROXIES}. X-Forwarded-For is read from
 * the right: each trusted proxy appends the address it received the request from, so the first
 * untrusted hop is the client and anything further left may have been supplied by that client.
 */
@Component
public class ClientAddressResolver {
  public static final String DEFAULT_TRUSTED_PROXIES =
      "127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,fc00::/7,fe80::/10";

  private final List<IpRange> trusted;
  private final String clientIpHeader;

  @Autowired
  public ClientAddressResolver(
      @Value("${app.security.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}") String trustedProxies,
      @Value("${app.security.client-ip-header:}") String clientIpHeader) {
    this.trusted = parseRanges(trustedProxies);
    this.clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.strip();
  }

  /** Resolver with the default private-network proxy ranges and no client-IP header. */
  public ClientAddressResolver() {
    this(DEFAULT_TRUSTED_PROXIES, "");
  }

  public String sourceAddress(HttpServletRequest request) {
    String remoteAddress = request.getRemoteAddr();
    String fallback =
        remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.strip();
    InetAddress peer = parseAddress(remoteAddress);
    if (peer == null || !isTrusted(peer)) return fallback;

    if (!clientIpHeader.isEmpty()) {
      InetAddress fromHeader = parseAddress(request.getHeader(clientIpHeader));
      if (fromHeader != null) return fromHeader.getHostAddress();
    }
    String client = peer.getHostAddress();
    List<String> forwarded = forwardedAddresses(request.getHeaders("X-Forwarded-For"));
    for (int i = forwarded.size() - 1; i >= 0; i--) {
      InetAddress hop = parseAddress(forwarded.get(i));
      if (hop == null) break; // Nothing left of a malformed entry can be trusted.
      client = hop.getHostAddress();
      if (!isTrusted(hop)) break;
    }
    return client;
  }

  boolean isTrusted(InetAddress address) {
    for (IpRange range : trusted) if (range.contains(address)) return true;
    return false;
  }

  static List<IpRange> parseRanges(String value) {
    String text = value == null ? "" : value.strip();
    if (text.isEmpty() || text.equalsIgnoreCase("none")) return List.of();
    List<IpRange> ranges = new ArrayList<>();
    for (String part : text.split(",")) {
      if (part.isBlank()) continue;
      try {
        ranges.add(IpRange.parse(part));
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "TRUSTED_PROXIES must list IP addresses or CIDR blocks separated by commas, or be 'none'.", e);
      }
    }
    return List.copyOf(ranges);
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
}
