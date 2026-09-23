import type { Metadata } from "next";
import type { ReactNode } from "react";
import { headers } from "next/headers";
import localFont from "next/font/local";
import { IBM_Plex_Mono, Newsreader, Outfit, Syne } from "next/font/google";

const geist = localFont({
  src: "../node_modules/@fontsource-variable/geist/files/geist-latin-wght-normal.woff2",
  variable: "--font-geist",
  display: "swap",
  weight: "100 900",
  adjustFontFallback: false,
});

const syne = Syne({
  subsets: ["latin"],
  variable: "--font-syne",
  display: "swap",
  weight: ["500", "600", "700", "800"],
});

const outfit = Outfit({
  subsets: ["latin"],
  variable: "--font-outfit",
  display: "swap",
});

const newsreader = Newsreader({
  subsets: ["latin"],
  variable: "--font-newsreader",
  display: "swap",
  style: ["normal", "italic"],
});

const plex = IBM_Plex_Mono({
  subsets: ["latin"],
  variable: "--font-plex",
  display: "swap",
  weight: ["400", "500"],
});

const publicUrl =
  process.env.PUBLIC_APP_URL || "https://hospital-ward-frontend.onrender.com";
const socialImage = "/opengraph-image";
const description =
  "Hospital operations with an in-workspace assistant for file-based planning and human-reviewed workflows.";

export const metadata: Metadata = {
  metadataBase: new URL(publicUrl),
  title: {
    default: "Medcore | The ward, in the moment.",
    template: "%s | Medcore",
  },
  description,
  applicationName: "Medcore",
  appleWebApp: { capable: true, title: "Medcore", statusBarStyle: "default" },
  openGraph: {
    title: "Medcore | The ward, in the moment.",
    description,
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
    title: "Medcore | The ward, in the moment.",
    description,
    images: [socialImage],
  },
  icons: { icon: "/favicon.svg" },
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  return (
    <html
      lang="en"
      className={`${geist.variable} ${syne.variable} ${outfit.variable} ${newsreader.variable} ${plex.variable}`}
    >
      <body>{children}</body>
    </html>
  );
}
