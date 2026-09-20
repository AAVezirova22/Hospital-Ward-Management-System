import type { Metadata } from "next";
import type { ReactNode } from "react";
import localFont from "next/font/local";
import "../src/style.css";
import "../src/design.css";
import "../src/cinematic.css";
import "../src/operations.css";
import "../src/command-center.css";

const geist = localFont({
  src: "../node_modules/@fontsource-variable/geist/files/geist-latin-wght-normal.woff2",
  variable: "--font-geist",
  display: "swap",
  weight: "100 900",
});

const publicUrl =
  process.env.PUBLIC_APP_URL || "https://hospital-ward-frontend.onrender.com";
const socialImage = "/opengraph-image";

export const metadata: Metadata = {
  metadataBase: new URL(publicUrl),
  title: "Medcore | Hospital Operations",
  description:
    "A calm command center for ward planning, care history, and human-confirmed operational assistance.",
  applicationName: "Medcore",
  appleWebApp: { capable: true, title: "Medcore", statusBarStyle: "default" },
  openGraph: {
    title: "Medcore | Hospital Operations",
    description:
      "Live ward planning, clear care history, and human-confirmed operational assistance.",
    type: "website",
    url: "/",
    siteName: "Medcore",
    images: [
      {
        url: socialImage,
        width: 1200,
        height: 630,
        alt: "Medcore hospital operations workspace",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: "Medcore | Hospital Operations",
    description: "A calm command center for ward planning and care operations.",
    images: [socialImage],
  },
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
          media="(min-width: 801px)"
          fetchPriority="high"
        />
        <link rel="preload" as="image" href="/medcore-atrium-640.webp" media="(max-width: 800px)" fetchPriority="high" />
      </head>
      <body>{children}</body>
    </html>
  );
}
