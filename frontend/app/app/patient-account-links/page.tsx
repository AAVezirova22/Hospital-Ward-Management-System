"use client";
import { useUser } from "../../../src/components/workspace";
import { PatientAccountReview } from "../../../src/features/administration/PatientAccountReview";
export default function Page() {
  return useUser().role === "ADMIN" ? <PatientAccountReview /> : <section><h1>Access restricted</h1><p>Administrator access required.</p><a href="/app/dashboard">Return to overview</a></section>;
}
