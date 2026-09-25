import { Suspense } from "react";
import { CareTasks } from "../../../src/features/care-pathways/CareTasks";
export default function Page() {
  return (
    <Suspense fallback={<p>Opening care tasks…</p>}>
      <CareTasks />
    </Suspense>
  );
}
