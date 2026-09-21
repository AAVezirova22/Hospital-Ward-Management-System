import { NextRequest, NextResponse } from "next/server";

// React's development build evaluates code at runtime and reports "eval() is not supported in this
// environment" under a policy without 'unsafe-eval'; the dev client also opens a hot-reload
// websocket. Both relaxations are development-only, so the deployed policy is unchanged.
const development = process.env.NODE_ENV !== "production";

function csp(nonce: string) {
  const development = process.env.NODE_ENV === "development";
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ""}`,
development
  ? "style-src 'self' 'unsafe-inline'"
  : `style-src 'self' 'nonce-${nonce}'`,
"style-src-attr 'unsafe-inline'",
    "img-src 'self' data:",
    development ? "connect-src 'self' ws: wss:" : "connect-src 'self'",
    "font-src 'self'",
    "media-src 'self'",
    "form-action 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "frame-ancestors 'none'",
  ].join("; ");
}

export function middleware(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const policy = csp(nonce);
  const headers = new Headers(request.headers);
  headers.set("x-nonce", nonce);
  headers.set("Content-Security-Policy", policy);
  const response = NextResponse.next({ request: { headers } });
  response.headers.set("Content-Security-Policy", policy);
  return response;
}

export const config = {
  matcher: [
    {
      source:
        "/((?!api|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|webp|avif)$).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
