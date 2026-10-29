import type { Metadata, Viewport } from 'next';
import type { ReactNode } from 'react';
import './globals.css';
import './public-landing.css';
import { SkipLink } from '../components/skip-link';
export const metadata: Metadata = {
  title: 'Cadence — Your creator workspace',
  description: 'A clear direction for your Brand.',
  robots: { index: false, follow: false },
};
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: '#f3f0e8',
};
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <SkipLink />
        {children}
      </body>
    </html>
  );
}
