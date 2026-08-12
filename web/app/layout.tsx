import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Web2App Builder',
  description: 'Turn any website into an Android APK and AAB.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
