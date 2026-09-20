"use client";
import { useUser } from "../../../src/components/workspace";
import { UserAdmin } from "../../../src/features/administration/UserAdmin";
export default function Page() {
  return useUser().role === "ADMIN" ? (
    <UserAdmin />
  ) : (
    <section>
      <h1>Access restricted</h1>
      <p>Administrator access required.</p>
      <a href="/app/dashboard">Return to overview</a>
    </section>
  );
}
