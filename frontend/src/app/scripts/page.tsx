'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { listScripts } from '@/lib/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { Script } from '@/types/script';
import {
  FileText,
  Clapperboard,
  Users,
  MessageSquare,
  Clock,
  Loader2,
  Plus,
  ArrowRight,
} from 'lucide-react';

const statusBadgeVariant = (status: string) => {
  switch (status) {
    case 'COMPLETED':
      return 'default' as const;
    case 'GENERATING':
      return 'accent' as const;
    case 'FAILED':
      return 'destructive' as const;
    default:
      return 'secondary' as const;
  }
};

const statusLabel = (status: string) => {
  switch (status) {
    case 'COMPLETED':
      return '已完成';
    case 'GENERATING':
      return '生成中';
    case 'FAILED':
      return '失败';
    case 'DRAFT':
      return '草稿';
    default:
      return status;
  }
};

export default function ScriptsPage() {
  const [scripts, setScripts] = useState<Script[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listScripts()
      .then(setScripts)
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
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
        </div>
      </header>

      <main className="flex-1 max-w-5xl mx-auto w-full px-6 py-12">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold flex items-center gap-3">
              <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-teal-500/10 ring-1 ring-teal-500/20">
                <Clapperboard className="h-4.5 w-4.5 text-teal-400" />
              </span>
              剧本列表
            </h1>
            <p className="text-muted-foreground text-sm mt-1.5 ml-12">
              管理所有已生成的剧本
            </p>
          </div>
          <Link href="/">
            <Button variant="outline" size="sm">
              <Plus className="h-4 w-4" />
              新建剧本
            </Button>
          </Link>
        </div>

        {/* Loading */}
        {loading && (
          <div className="flex items-center justify-center py-24">
            <div className="flex flex-col items-center gap-3">
              <Loader2 className="h-8 w-8 animate-spin text-teal-400" />
              <p className="text-sm text-muted-foreground">加载剧本列表...</p>
            </div>
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="text-center py-24">
            <p className="text-sm text-red-400">{error}</p>
            <Button variant="outline" size="sm" className="mt-3" onClick={() => window.location.reload()}>
              重试
            </Button>
          </div>
        )}

        {/* Script cards */}
        {!loading && !error && scripts.length === 0 && (
          <div className="text-center py-24">
            <div className="inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-white/5 mb-4">
              <Clapperboard className="h-7 w-7 text-muted-foreground" />
            </div>
            <p className="text-muted-foreground">暂无剧本</p>
            <p className="text-xs text-muted-foreground mt-1 mb-4">
              上传小说并配置生成参数来创建第一个剧本
            </p>
            <Link href="/">
              <Button variant="gradient" size="sm">
                开始创建
                <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
          </div>
        )}

        {!loading && !error && scripts.length > 0 && (
          <div className="grid gap-3">
            {scripts.map((script) => (
              <Link
                key={script.id}
                href={`/scripts/${script.id}`}
                className="block rounded-xl border border-white/5 bg-white/[0.02] p-5 card-lift group"
              >
                <div className="flex items-center justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      <h2 className="font-semibold text-sm group-hover:text-teal-400 transition-colors truncate">
                        {script.title || `剧本 #${script.id}`}
                      </h2>
                      <Badge variant={statusBadgeVariant(script.status)} className="text-[10px] shrink-0">
                        {statusLabel(script.status)}
                      </Badge>
                    </div>
                    <div className="flex items-center gap-4 text-xs text-muted-foreground">
                      <span className="flex items-center gap-1">
                        <Clapperboard className="h-3 w-3" />
                        {script.sceneCount} 场景
                      </span>
                      <span className="flex items-center gap-1">
                        <Users className="h-3 w-3" />
                        {script.characterCount} 角色
                      </span>
                      <span className="flex items-center gap-1">
                        <MessageSquare className="h-3 w-3" />
                        {script.dialogueCount} 对白
                      </span>
                      {script.status === 'GENERATING' && (
                        <span className="flex items-center gap-1 text-teal-400">
                          <Clock className="h-3 w-3" />
                          {Math.round(script.progress)}%
                        </span>
                      )}
                    </div>
                    {/* Progress bar for generating scripts */}
                    {script.status === 'GENERATING' && (
                      <div className="mt-3 h-1 bg-white/5 rounded-full overflow-hidden max-w-xs">
                        <div
                          className="h-full rounded-full transition-all duration-700"
                          style={{
                            width: `${script.progress}%`,
                            background: 'linear-gradient(90deg, oklch(0.72 0.14 185), oklch(0.65 0.16 200))',
                          }}
                        />
                      </div>
                    )}
                  </div>
                  <ArrowRight className="h-4 w-4 text-muted-foreground group-hover:text-teal-400 group-hover:translate-x-0.5 transition-all shrink-0 ml-3" />
                </div>
              </Link>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
