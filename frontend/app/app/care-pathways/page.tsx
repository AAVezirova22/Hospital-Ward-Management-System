import { Suspense } from "react";
import { CarePathways } from "../../../src/features/care-pathways/CarePathways";
export default function Page() {
  return (
    <Suspense fallback={<p>Opening care pathway studio…</p>}>
      <CarePathways />
    </Suspense>
  );
}
