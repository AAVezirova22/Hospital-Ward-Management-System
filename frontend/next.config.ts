import type { NextConfig } from "next";

function resolveApiOrigin() {
  const hostport = process.env.API_INTERNAL_HOSTPORT?.trim();
  const configured =
    process.env.API_INTERNAL_URL?.trim() ||
    (hostport ? `http://${hostport}` : undefined);
  if (configured) return configured.replace(/\/$/, "");
  if (process.env.RENDER === "true") {
    throw new Error(
      "The hospital API target is missing. Set API_INTERNAL_HOSTPORT to the backend's " +
        "Render private host:port, or set API_INTERNAL_URL before building the frontend.",
    );
  }
  return "http://127.0.0.1:8080";
}

const apiOrigin = resolveApiOrigin();

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  // A bounded eight-step assistant turn can outlast the default 30-second proxy timeout.
  experimental: { proxyTimeout: 150_000 },
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

export default nextConfig;
