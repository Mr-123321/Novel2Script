'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import {
  FileText,
  Github,
  Sparkles,
  Clapperboard,
  FileCode,
  ArrowRight,
  Wand2,
  BookOpen,
  Clock,
} from 'lucide-react';
import { UploadZone } from '@/components/novel/UploadZone';
import { listScripts } from '@/lib/api';
import type { Script } from '@/types/script';

const features = [
  {
    icon: Wand2,
    title: '智能解析',
    description: 'AI 自动识别章节结构、人物关系、关键事件',
    color: 'teal',
  },
  {
    icon: Clapperboard,
    title: '场景生成',
    description: '按地点、时间、冲突自动切分影视场景',
    color: 'purple',
  },
  {
    icon: FileCode,
    title: '标准导出',
    description: 'YAML 格式输出，符合行业剧本规范',
    color: 'blue',
  },
];

export default function HomePage() {
  const [recentScripts, setRecentScripts] = useState<Script[]>([]);

  useEffect(() => {
    listScripts()
      .then((scripts) => setRecentScripts(scripts.slice(0, 4)))
      .catch(() => {}); // silently ignore on landing page
  }, []);

  return (
    <div className="flex flex-col min-h-screen">
      {/* Header */}
      <header className="glass border-b border-white/5 sticky top-0 z-50">
        <div className="max-w-5xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link
            href="/"
            className="flex items-center gap-2.5 font-semibold text-lg group"
          >
            <div className="h-8 w-8 rounded-lg bg-teal-500/15 flex items-center justify-center ring-1 ring-teal-500/20 group-hover:bg-teal-500/25 transition-colors">
              <FileText className="h-4 w-4 text-teal-400" />
            </div>
            <span className="gradient-text">Novel2Script</span>
          </Link>
          <nav className="flex items-center gap-6 text-sm text-muted-foreground">
            <Link
              href="/scripts"
              className="hover:text-foreground transition-colors flex items-center gap-1"
            >
              剧本列表
              <ArrowRight className="h-3 w-3 opacity-0 -translate-x-2 group-hover:opacity-100 group-hover:translate-x-0 transition-all" />
            </Link>
            <a
              href="https://github.com/Mr-123321/Novel2Script"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-foreground transition-colors p-1.5 rounded-lg hover:bg-white/5"
            >
              <Github className="h-5 w-5" />
            </a>
          </nav>
        </div>
      </header>

      {/* Hero */}
      <main className="flex-1 flex flex-col items-center justify-center px-6 py-20">
        {/* Badge */}
        <div className="animate-fade-in mb-8">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full glass border border-white/10 text-sm text-muted-foreground">
            <Sparkles className="h-3.5 w-3.5 text-teal-400" />
            <span>AI 驱动的剧本转换引擎</span>
          </div>
        </div>

        {/* Title */}
        <h1
          className="text-5xl sm:text-6xl font-bold tracking-tight text-center mb-4 animate-slide-up"
          style={{ animationDelay: '0.1s', animationFillMode: 'both' }}
        >
          <span className="gradient-text">小说</span>
          <span className="text-foreground"> → </span>
          <span className="gradient-text-accent">剧本</span>
        </h1>
        <p
          className="text-muted-foreground text-lg mb-12 max-w-lg text-center leading-relaxed animate-slide-up"
          style={{ animationDelay: '0.2s', animationFillMode: 'both' }}
        >
          上传小说，AI 自动分析人物、切分场景、生成对白
          <br />
          输出标准影视剧本格式
        </p>

        {/* Upload Card */}
        <div
          className="w-full max-w-2xl animate-slide-up"
          style={{ animationDelay: '0.3s', animationFillMode: 'both' }}
        >
          <div className="gradient-border p-[1px] rounded-2xl">
            <div className="rounded-2xl glass p-8">
              <UploadZone />
            </div>
          </div>
        </div>

        {/* Features */}
        <div
          className="mt-16 grid grid-cols-1 sm:grid-cols-3 gap-4 w-full max-w-2xl animate-slide-up"
          style={{ animationDelay: '0.4s', animationFillMode: 'both' }}
        >
          {features.map((feature, i) => (
            <div
              key={feature.title}
              className="group relative rounded-xl glass border border-white/5 p-5 text-center card-lift cursor-default"
              style={{
                animationDelay: `${0.5 + i * 0.1}s`,
                animationFillMode: 'both',
              }}
            >
              <div
                className={`inline-flex h-10 w-10 items-center justify-center rounded-lg mb-3 ring-1 transition-colors ${
                  feature.color === 'teal'
                    ? 'bg-teal-500/10 ring-teal-500/20 text-teal-400'
                    : feature.color === 'purple'
                    ? 'bg-purple-500/10 ring-purple-500/20 text-purple-400'
                    : 'bg-blue-500/10 ring-blue-500/20 text-blue-400'
                }`}
              >
                <feature.icon className="h-5 w-5" />
              </div>
              <h3 className="font-semibold text-sm mb-1">{feature.title}</h3>
              <p className="text-xs text-muted-foreground leading-relaxed">
                {feature.description}
              </p>
            </div>
          ))}
        </div>

        {/* Recent projects */}
        {recentScripts.length > 0 && (
          <div
            className="mt-16 w-full max-w-2xl animate-slide-up"
            style={{ animationDelay: '0.7s', animationFillMode: 'both' }}
          >
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-semibold text-muted-foreground flex items-center gap-2">
                <BookOpen className="h-4 w-4" />
                最近的项目
              </h2>
              <Link
                href="/scripts"
                className="text-xs text-teal-400 hover:text-teal-300 transition-colors flex items-center gap-1"
              >
                查看全部
                <ArrowRight className="h-3 w-3" />
              </Link>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {recentScripts.map((script) => (
                <Link
                  key={script.id}
                  href={`/scripts/${script.id}`}
                  className="rounded-xl border border-white/5 bg-white/[0.02] p-4 card-lift group text-left"
                >
                  <div className="flex items-center gap-2 mb-2">
                    <div className="h-7 w-7 rounded-lg bg-teal-500/10 flex items-center justify-center shrink-0">
                      <FileText className="h-3 w-3 text-teal-400" />
                    </div>
                    <span className="text-xs font-medium truncate group-hover:text-teal-400 transition-colors">
                      {script.title || `剧本 #${script.id}`}
                    </span>
                  </div>
                  <div className="flex items-center gap-3 text-[10px] text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <Clapperboard className="h-2.5 w-2.5" />
                      {script.sceneCount}场
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="h-2.5 w-2.5" />
                      {script.status === 'GENERATING'
                        ? `${Math.round(script.progress)}%`
                        : script.status === 'COMPLETED'
                        ? '完成'
                        : '草稿'}
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-white/5 py-8 text-center text-xs text-muted-foreground">
        <p>Novel2Script — 支持 .txt / .md 格式，至少 3 章以上</p>
      </footer>
    </div>
  );
}
