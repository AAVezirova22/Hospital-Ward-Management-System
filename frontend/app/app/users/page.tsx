"use client";
import { useUser } from "../../../src/components/workspace";
import { Catalogue } from "../../../src/features/administration/Catalogue";
export default function Page() {
  return useUser().role === "ADMIN" ? (
    <Catalogue kind="users" />
  ) : (
    <section>
      <h1>Access restricted</h1>
      <p>Administrator access required.</p>
      <a href="/app/dashboard">Return to overview</a>
    </section>
  );
}
