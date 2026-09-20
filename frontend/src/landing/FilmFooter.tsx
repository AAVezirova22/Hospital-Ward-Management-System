import Link from "next/link";

export function FilmFooter() {
  return (
    <footer className="film-footer">
      <div className="film-footer-row">
        <strong>Medcore</strong>
        <nav aria-label="Footer">
          <a href="#specialists">Specialists</a>
          <a href="#visit">Visit</a>
          <Link href="/app">Staff sign-in</Link>
        </nav>
      </div>
      <small>
        Medcore Hospital, Sofia. Clinical records stay in the department that
        holds them.
      </small>
    </footer>
  );
}
