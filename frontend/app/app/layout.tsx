import MedcoreApp from "../../src/main";
import type { ReactNode } from "react";
export default function WorkspaceLayout({ children }: { children: ReactNode }) {
  return <MedcoreApp>{children}</MedcoreApp>;
}
