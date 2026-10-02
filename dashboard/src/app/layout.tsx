import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BlinkWell Research Portal — By Mitali Purohit",
  description: "Researcher Analytics & Data Dashboard for BlinkWell Eye Health Monitoring",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="antialiased min-h-screen bg-slate-50 text-slate-900">
        {children}
      </body>
    </html>
  );
}
