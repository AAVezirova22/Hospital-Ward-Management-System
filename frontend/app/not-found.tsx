import Link from "next/link";
export default function NotFound() {
  return (
    <main className="boot">
      <span className="eyebrow">MEDCORE / 404</span>
      <h1>This room isn’t on the map.</h1>
      <p>The page may have moved or the address is incomplete.</p>
      <Link className="primary" href="/app/dashboard">
        Return to workspace
      </Link>
    </main>
  );
}
