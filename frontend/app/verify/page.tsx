"use client";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CinematicProvider } from "../../src/cinematic";
import { LoginScene } from "../../src/login-scene";
import { EmailVerification } from "../../src/features/auth/Registration";

const client = new QueryClient({
  defaultOptions: { queries: { retry: false } },
});

function VerifyForm() {
  const token = useSearchParams().get("token") || "";
  return (
    <LoginScene>
      <EmailVerification token={token} onBack={() => (window.location.href = "/")} />
    </LoginScene>
  );
}

export default function VerifyPage() {
  return (
    <QueryClientProvider client={client}>
      <CinematicProvider>
        <Suspense fallback={<p>Opening confirmation…</p>}>
          <VerifyForm />
        </Suspense>
      </CinematicProvider>
    </QueryClientProvider>
  );
}
