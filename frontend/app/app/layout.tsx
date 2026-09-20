import MedcoreApp from "../../src/main";
import type { ReactNode } from "react";
import "../../src/style.css";
import "../../src/design.css";
import "../../src/cinematic.css";
import "../../src/operations.css";
import "../../src/command-center.css";

export default function WorkspaceLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <link
        rel="preload"
        as="image"
        href="/medcore-atrium.webp"
        media="(min-width: 801px)"
        fetchPriority="high"
      />
      <link
        rel="preload"
        as="image"
        href="/medcore-atrium-640.webp"
        media="(max-width: 800px)"
        fetchPriority="high"
      />
      <MedcoreApp>{children}</MedcoreApp>
    </>
  );
}
