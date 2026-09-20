"use client";
import { useUser } from "../../../src/components/workspace";
import { Audit } from "../../../src/features/administration/Audit";
export default function Page() {
  return useUser().role === "ADMIN" ? (
    <Audit />
  ) : (
    <section>
      <h1>Access restricted</h1>
      <p>Administrator access required.</p>
      <a href="/app/dashboard">Return to overview</a>
    </section>
  );
}
