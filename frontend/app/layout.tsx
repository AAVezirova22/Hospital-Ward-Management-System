import type { Metadata } from "next";
import type { ReactNode } from "react";
import localFont from "next/font/local";
import "../src/style.css";
import "../src/design.css";
import "../src/cinematic.css";
import "../src/operations.css";

const geist = localFont({
  src: "../node_modules/@fontsource-variable/geist/files/geist-latin-wght-normal.woff2",
  variable: "--font-geist",
  display: "swap",
  weight: "100 900",
});

export const metadata: Metadata = {
  title: "Medcore | Hospital Operations",
  description: "Hospital department operations workspace",
  icons: { icon: "/favicon.svg" },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" className={geist.variable}>
      <head>
        <link
          rel="preload"
          as="image"
          href="/medcore-atrium.webp"
          fetchPriority="high"
        />
      </head>
      <body>{children}</body>
    </html>
  );
}
