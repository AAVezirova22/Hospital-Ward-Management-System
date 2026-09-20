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
  // App Router still emits a small inline boot script, so CSP keeps 'unsafe-inline'
  // plus 'strict-dynamic' until a nonce pipeline is wired through Next 16.
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

export default nextConfig;
