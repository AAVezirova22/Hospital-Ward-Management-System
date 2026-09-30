import MedcoreApp from "../../src/main";
import type { ReactNode } from "react";
import { headers } from "next/headers";
import "../../src/style.css";
import "../../src/design.css";
import "../../src/cinematic.css";
import "../../src/operations.css";
import "../../src/command-center.css";
import "../../src/care-pathways.css";

export default async function WorkspaceLayout({
  children,
}: {
  children: ReactNode;
}) {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
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
      <MedcoreApp nonce={nonce}>{children}</MedcoreApp>
    </>
  );
}
