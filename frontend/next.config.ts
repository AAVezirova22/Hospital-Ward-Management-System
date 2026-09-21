import type { NextConfig } from "next";
import {
  PHASE_PRODUCTION_BUILD,
  PHASE_PRODUCTION_SERVER,
} from "next/constants";

const LOOPBACK_API = "http://127.0.0.1:8080";

/**
 * Where same-origin `/api` requests are proxied.
 *
 * Next bakes rewrite destinations into `.next/routes-manifest.json` at build
 * time, so the API address must be available during `next build`.
 */
function resolveApiOrigin(phase: string) {
  const hostport = process.env.API_INTERNAL_HOSTPORT?.trim();
  const configured =
    process.env.API_INTERNAL_URL?.trim() ||
    (hostport ? `http://${hostport}` : "");

  if (configured) return configured.replace(/\/$/, "");

  if (
    phase === PHASE_PRODUCTION_BUILD ||
    phase === PHASE_PRODUCTION_SERVER
  ) {
    throw new Error(
      "The hospital API target is missing. Set API_INTERNAL_URL " +
        "(http://host:port) or API_INTERNAL_HOSTPORT (host:port) before " +
        "building the frontend. On Render, use the backend service's private " +
        "host:port and rebuild the frontend after it changes.",
    );
  }

  return LOOPBACK_API;
}

const nextConfig = (phase: string): NextConfig => {
  const apiOrigin = resolveApiOrigin(phase);

  return {
    output: "standalone",
    poweredByHeader: false,

    // A bounded eight-step assistant turn can outlast the default
    // 30-second proxy timeout.
    experimental: {
      proxyTimeout: 150_000,
    },

    // middleware.ts owns the nonce-bearing CSP for document responses.
    async rewrites() {
      return [
        {
          source: "/api/:path*",
          destination: `${apiOrigin}/api/:path*`,
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
};

export default function config(phase: string): NextConfig {
  const origin = apiOrigin(phase);
  return {
    output: "standalone",
    poweredByHeader: false,
    // A bounded eight-step assistant turn can outlast the default 30-second proxy timeout.
    experimental: { proxyTimeout: 150_000 },
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
