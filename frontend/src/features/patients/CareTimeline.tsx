import type { AdmissionView } from "../../api/contracts";
import { date } from "../../api";
import { careTimeline } from "./timeline";

export function CareTimeline({ admissions }: { admissions: AdmissionView[] }) {
  const active = admissions.find((v) => v.admission.status === "ACTIVE");
  return (
    <section
      className="panel care-timeline"
      aria-label="Unified patient timeline"
    >
      <h2>Care journey</h2>
      <ol>
        {careTimeline(admissions).map((e) => (
          <li key={e.id} className={`care-event ${e.kind}`}>
            <time dateTime={e.at}>{date(e.at)}</time>
            <div>
              <strong>{e.title}</strong>
              <p>{e.detail}</p>
              <small>{e.admissionNumber}</small>
            </div>
          </li>
        ))}
        {active && (
          <li className="care-event pending">
            <span>Next step</span>
            <div>
              <strong>
                {active.admission.expectedDischargeDate
                  ? "Discharge planned"
                  : "Care in progress"}
              </strong>
              <p>
                {active.admission.expectedDischargeDate
                  ? `Expected ${active.admission.expectedDischargeDate} · subject to confirmation`
                  : "No discharge date scheduled"}
              </p>
            </div>
          </li>
        )}
      </ol>
      {!admissions.length && <p>No care events recorded yet.</p>}
    </section>
  );
}
