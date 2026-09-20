"use client";
export default function WorkspaceError({reset}:{reset:()=>void}) {return <main className="boot"><h1>We couldn’t open this view.</h1><p>Your saved records are still available. Try loading the workspace again.</p><button className="primary" onClick={reset}>Try again</button><a href="/app/dashboard">Return to overview</a></main>;}
