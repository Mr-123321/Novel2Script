import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  title: 'Novel2Script — AI 小说转剧本',
  description:
    '基于 AI 的小说自动转换剧本系统，支持人物抽取、剧情分析、场景切分、对白生成',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" className="dark">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased bg-background text-foreground min-h-screen relative`}
      >
        {/* Ambient background orbs */}
        <div className="fixed inset-0 overflow-hidden pointer-events-none z-0">
          <div
            className="glow-orb w-[600px] h-[600px] -top-40 -right-40"
            style={{
              background:
                'radial-gradient(circle, oklch(0.72 0.14 185 / 0.2), transparent 70%)',
            }}
          />
          <div
            className="glow-orb w-[500px] h-[500px] -bottom-32 -left-32"
            style={{
              background:
                'radial-gradient(circle, oklch(0.68 0.13 280 / 0.15), transparent 70%)',
            }}
          />
          <div
            className="glow-orb w-[400px] h-[400px] top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2"
            style={{
              background:
                'radial-gradient(circle, oklch(0.72 0.14 185 / 0.08), transparent 70%)',
            }}
          />
        </div>

        {/* Subtle dot pattern overlay */}
        <div
          className="fixed inset-0 pointer-events-none z-0 opacity-[0.03]"
          style={{
            backgroundImage:
              'radial-gradient(circle, oklch(1 0 0 / 1) 1px, transparent 1px)',
            backgroundSize: '32px 32px',
          }}
        />

        {/* Page content */}
        <div className="relative z-10">{children}</div>
      </body>
    </html>
  );
}
