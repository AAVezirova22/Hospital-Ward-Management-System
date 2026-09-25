"use client";
import { useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, type Row } from "../../api";
import { ErrorBox, Modal, useAllPages } from "../../components/workspace";

type Kind = "patients" | "doctors" | "rooms" | "procedures";
type ImportRow = { line: number; values: Row; errors: string[] };
const columns: Record<Kind, string[]> = {
  patients: ["patientIdentifier", "firstName", "lastName", "dateOfBirth", "phoneNumber", "address"],
  doctors: ["doctorIdentifier", "firstName", "lastName", "specialty", "active"],
  rooms: ["roomNumber", "bedCount", "active", "capabilities"],
  procedures: ["procedureCode", "procedureName", "currentCost", "active"],
};
const required: Record<Kind, string[]> = {
  patients: ["patientIdentifier", "firstName", "lastName", "dateOfBirth"],
  doctors: ["doctorIdentifier", "firstName", "lastName", "specialty"],
  rooms: ["roomNumber", "bedCount"],
  procedures: ["procedureCode", "procedureName", "currentCost"],
};
function parseCsv(input: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [], value = "", quoted = false;
  for (let i = 0; i < input.length; i++) {
    const c = input[i];
    if (quoted) {
      if (c === '"' && input[i + 1] === '"') { value += '"'; i++; }
      else if (c === '"') quoted = false;
      else value += c;
    } else if (c === '"' && value.length === 0) quoted = true;
    else if (c === ",") { row.push(value.trim()); value = ""; }
    else if (c === "\n" || c === "\r") {
      if (c === "\r" && input[i + 1] === "\n") i++;
      row.push(value.trim());
      if (row.some((cell) => cell)) rows.push(row);
      row = []; value = "";
    } else value += c;
  }
  row.push(value.trim());
  if (row.some((cell) => cell)) rows.push(row);
  if (quoted) throw new Error("The CSV has an unclosed quoted field.");
  return rows;
}
function identity(kind: Kind, row: Row) {
  return String(row[{ patients: "patientIdentifier", doctors: "doctorIdentifier", rooms: "roomNumber", procedures: "procedureCode" }[kind]] ?? "").trim().toLocaleLowerCase();
}
function validDateOfBirth(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value && parsed <= new Date();
}
function convert(kind: Kind, values: Row): Row {
  const result = { ...values };
  if (kind === "rooms") {
    result.bedCount = Number(result.bedCount);
    result.active = result.active === "" ? true : result.active.toLowerCase() !== "false";
    result.capabilities = String(result.capabilities ?? "").split("|").map((x: string) => x.trim()).filter(Boolean);
  }
  if (kind === "procedures") {
    result.currentCost = Number(result.currentCost);
    result.active = result.active === "" ? true : result.active.toLowerCase() !== "false";
  }
  if (kind === "doctors") result.active = result.active === "" ? true : result.active.toLowerCase() !== "false";
  return result;
}

export function CsvImport({ kind, onClose }: { kind: Kind; onClose: () => void }) {
  const client = useQueryClient();
  const { data: existing = [], error: existingError, isLoading: existingLoading } = useAllPages<Row>(`/${kind}`);
  const [fileRows, setFileRows] = useState<ImportRow[] | null>(null);
  const [failure, setFailure] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [summary, setSummary] = useState<{ created: number; skipped: number } | null>(null);
  const [commitErrors, setCommitErrors] = useState<Record<number, string>>({});
  const preview = useMemo(() => {
    if (!fileRows) return [];
    const seen = new Set(existing.map((r) => identity(kind, r)));
    return fileRows.map((r) => {
      const errors = [...r.errors];
      const key = identity(kind, r.values);
      if (!key) errors.push("Missing unique identifier");
      else if (seen.has(key)) errors.push("Duplicate identifier; row will be skipped");
      else seen.add(key);
      for (const field of required[kind]) if (!String(r.values[field] ?? "").trim()) errors.push(`Missing ${field}`);
      if (kind === "patients" && r.values.dateOfBirth && !validDateOfBirth(String(r.values.dateOfBirth))) errors.push("Date of birth must be a real YYYY-MM-DD date and not in the future");
      if (kind === "rooms" && (!Number.isInteger(Number(r.values.bedCount)) || Number(r.values.bedCount) < 1 || Number(r.values.bedCount) > 100)) errors.push("Bed count must be between 1 and 100");
      if (kind === "procedures" && (!Number.isFinite(Number(r.values.currentCost)) || Number(r.values.currentCost) < 0)) errors.push("Cost must be zero or greater");
      if (kind !== "patients" && r.values.active && !["true", "false"].includes(String(r.values.active).toLowerCase())) errors.push("Active must be true or false");
      if (kind === "patients" && r.values.phoneNumber && !/^\+[1-9]\d{7,14}$/.test(r.values.phoneNumber)) errors.push("Phone must use E.164 format");
      return { ...r, errors: [...new Set(errors)] };
    });
  }, [fileRows, existing, kind]);
  async function read(file?: File) {
    if (!file) return;
    setFailure(null); setSummary(null);
    try {
      const matrix = parseCsv(await file.text());
      if (matrix.length < 2) throw new Error("Include a header row and at least one data row.");
      const headers = matrix[0].map((h) => h.replace(/^\uFEFF/, "").trim());
      const allowed = columns[kind];
      const unknown = headers.filter((h) => !allowed.includes(h));
      const missing = required[kind].filter((h) => !headers.includes(h));
      const repeated = headers.filter((h, i) => headers.indexOf(h) !== i);
      if (unknown.length || missing.length || repeated.length) throw new Error([unknown.length ? `Unknown columns: ${unknown.join(", ")}` : "", missing.length ? `Required columns: ${missing.join(", ")}` : "", repeated.length ? `Repeated columns: ${[...new Set(repeated)].join(", ")}` : ""].filter(Boolean).join(". "));
      setFileRows(matrix.slice(1).map((cells, index) => {
        const values: Row = {};
        headers.forEach((header, i) => { values[header] = cells[i] ?? ""; });
        const extras = cells.length > headers.length ? ["More values than columns"] : [];
        return { line: index + 2, values, errors: extras };
      }));
    } catch (e) { setFileRows(null); setFailure(e); }
  }
  async function commit() {
    setBusy(true); setFailure(null);
    let created = 0, skipped = preview.filter((r) => r.errors.length).length;
    const rowErrors: Record<number, string> = {};
    try {
      for (const row of preview) {
        if (row.errors.length) continue;
        try { await api(`/${kind}`, "POST", convert(kind, row.values)); created++; }
        catch (e) { skipped++; rowErrors[row.line] = e instanceof Error ? e.message : "The server rejected this row."; }
      }
      setCommitErrors(rowErrors);
      setSummary({ created, skipped });
      await client.invalidateQueries();
    } catch (e) { setFailure(e); }
    finally { setBusy(false); }
  }
  const template = columns[kind].join(",") + "\n";
  return <Modal title={`Import ${kind}`} onClose={onClose}>
    <p>Upload a CSV with these column names: <code>{columns[kind].join(", ")}</code>. Rooms accept capabilities separated by |. Leave active blank to default to true.</p>
    <label>CSV file<input type="file" accept=".csv,text/csv" onChange={(e) => void read(e.target.files?.[0])} /></label>
    <ErrorBox error={failure} />
    <ErrorBox error={existingError} />
    {fileRows && <>
      <p>{preview.filter((r) => !r.errors.length).length} ready · {preview.filter((r) => r.errors.length).length} will be skipped</p>
      <div className="table-panel"><table><thead><tr><th>Line</th><th>Identifier</th><th>Result</th><th>Details</th></tr></thead><tbody>{preview.map((r) => <tr key={r.line}><td>{r.line}</td><td>{identity(kind, r.values)}</td><td>{r.errors.length ? "Skip" : summary ? commitErrors[r.line] ? "Skipped" : "Created" : "Ready"}</td><td>{r.errors.join("; ") || commitErrors[r.line] || (summary ? commitErrors[r.line] ? "Server rejected this row" : "Valid" : "Valid")}</td></tr>)}</tbody></table></div>
      {!summary ? <button className="primary" disabled={busy || existingLoading || !!existingError || !preview.some((r) => !r.errors.length)} onClick={() => void commit()}>{busy ? "Importing…" : "Commit valid rows"}</button> : <p role="status">Import complete: {summary.created} created, {summary.skipped} skipped.</p>}
    </>}
    <details><summary>CSV template</summary><pre>{template}</pre></details>
  </Modal>;
}
