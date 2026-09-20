import type { NextConfig } from "next";

const apiOrigin = (
  process.env.API_INTERNAL_URL ||
  (process.env.API_INTERNAL_HOSTPORT
    ? `http://${process.env.API_INTERNAL_HOSTPORT}`
    : "http://127.0.0.1:8080")
).replace(/\/$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  // A bounded eight-step assistant turn can outlast the default 30-second proxy timeout.
  experimental: { proxyTimeout: 150_000 },
  // App Router still emits a small inline boot script. 'strict-dynamic' without a
  // nonce blocks Next chunks, so CSP keeps 'unsafe-inline' until a nonce pipeline
  // is wired through Next 16.
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
      {
        key: "Content-Security-Policy",
        value: [
          "default-src 'self'",
          process.env.NODE_ENV === "production"
            ? "script-src 'self' 'unsafe-inline'"
            : "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
          "style-src 'self' 'unsafe-inline'",
          "img-src 'self' data:",
          "media-src 'self'",
          process.env.NODE_ENV === "production"
            ? "connect-src 'self'"
            : "connect-src 'self' ws: wss:",
          "font-src 'self'",
          "object-src 'none'",
          "base-uri 'self'",
          "frame-ancestors 'none'",
        ].join("; "),
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

export default nextConfig;
