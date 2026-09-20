import type { NextConfig } from "next";
import {
  PHASE_PRODUCTION_BUILD,
  PHASE_PRODUCTION_SERVER,
} from "next/constants";

const LOOPBACK_API = "http://127.0.0.1:8080";

/**
 * Where same-origin `/api` requests are proxied.
 *
 * Next bakes rewrite destinations into `.next/routes-manifest.json` at build time, so this value
 * is fixed when `next build` runs: setting the variable on a deployed service and restarting it
 * changes nothing until the service is built again. A deployed web service also has no Java
 * process of its own, so silently falling back to loopback there turns a missing address into an
 * unexplained 502 on every API call. Outside development the address is therefore required.
 */
function apiOrigin(phase: string) {
  const configured =
    process.env.API_INTERNAL_URL ||
    (process.env.API_INTERNAL_HOSTPORT
      ? `http://${process.env.API_INTERNAL_HOSTPORT}`
      : "");
  if (configured) return configured.replace(/\/$/, "");
  if (phase === PHASE_PRODUCTION_BUILD || phase === PHASE_PRODUCTION_SERVER)
    throw new Error(
      "The hospital API address is not configured. Set API_INTERNAL_URL " +
        "(http://host:port) or API_INTERNAL_HOSTPORT (host:port) to the API service's internal " +
        "address before building this service. On Render the value is the API service's " +
        "Connect → Internal address, and the web service must be rebuilt after it changes, " +
        "because the proxy target is written into the build.",
    );
  return LOOPBACK_API;
}

export default function config(phase: string): NextConfig {
  const origin = apiOrigin(phase);
  return {
    output: "standalone",
    poweredByHeader: false,
    // A bounded eight-step assistant turn can outlast the default 30-second proxy timeout.
    experimental: { proxyTimeout: 150_000 },
    // App Router still emits a small inline boot script, so CSP keeps 'unsafe-inline'
    // plus 'strict-dynamic' until a nonce pipeline is wired through Next 16.
    async rewrites() {
      return [
        {
          source: "/api/:path*",
          destination: `${origin}/api/:path*`,
        },
      ];
    },
    async headers() {
      const security = [
        { key: "X-Content-Type-Options", value: "nosniff" },
        { key: "Referrer-Policy", value: "no-referrer" },
        { key: "X-Frame-Options", value: "DENY" },
        {
          key: "Permissions-Policy",
          value: "camera=(), microphone=(), geolocation=(), payment=()",
        },
      ];
      if (
        process.env.COOKIE_SECURE === "true" ||
        process.env.NODE_ENV === "production"
      ) {
        security.push({
          key: "Strict-Transport-Security",
          value: "max-age=63072000; includeSubDomains; preload",
        });
      }
      return [{ source: "/:path*", headers: security }];
    },
  };
}
