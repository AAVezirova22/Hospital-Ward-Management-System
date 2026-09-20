import { notFound } from "next/navigation";
import { PatientDetail } from "../../../../src/features/patients/PatientDetail";
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  if (!/^[\w.%+-]+$/.test(id)) notFound();
  return <PatientDetail id={id} />;
}
