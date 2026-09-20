import Link from "next/link";

export function FilmFooter() {
  return (
    <footer className="film-footer">
      <div className="film-footer-row">
        <strong>Medcore</strong>
        <nav aria-label="Footer">
          <a href="#assistant">Assistant</a>
          <a href="#workflow">Workflow</a>
          <Link href="/app">Open the workspace</Link>
        </nav>
      </div>
      <small>
        Clinical records stay in the department that holds them. AI writes need
        an owned, expiring confirmation.
      </small>
    </footer>
  );
}
