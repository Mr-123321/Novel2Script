'use client';

import { useEffect, useState, useRef, useCallback } from 'react';
import { useParams } from 'next/navigation';
import { useScriptStore } from '@/stores/script-store';
import { useSSE } from '@/hooks/use-sse';
import { getScript } from '@/lib/api';
import { ScriptEditor } from '@/components/script/ScriptEditor';
import type { WorkflowProgress as WorkflowProgressType } from '@/types/script';
import Link from 'next/link';
import { FileText, AlertCircle, Loader2 } from 'lucide-react';

/** ────────────────────────────────────────────
 *  Loading Progress Bar — ~4 minute gradual animation
 * ──────────────────────────────────────────── */
function LoadingProgress({
  scriptStatus,
  sseProgress,
}: {
  scriptStatus: string | undefined;
  sseProgress: number;
}) {
  const [progress, setProgress] = useState(0);
  const [visible, setVisible] = useState(true);
  const startRef = useRef(Date.now());
  const resolvedRef = useRef(false);

  const TOTAL_SEC = 240; // 4 minutes
  const CAP = 95;

  useEffect(() => {
    const tick = () => {
      if (resolvedRef.current) return;

      const elapsed = (Date.now() - startRef.current) / 1000;
      // Exponential approach to cap
      const simulated =
        5 + (CAP - 5) * (1 - Math.exp(-elapsed / 55));

      // If SSE reports higher, use it (but don't exceed cap)
      const current = Math.max(
        Math.min(simulated, CAP),
        Math.min(sseProgress, CAP)
      );

      setProgress(current);
    };

    tick();
    const interval = setInterval(tick, 200);
    return () => clearInterval(interval);
  }, [sseProgress]);

  // When script resolves
  useEffect(() => {
    if (scriptStatus === 'COMPLETED' || scriptStatus === 'FAILED') {
      resolvedRef.current = true;
      // Animate to 100%
      setProgress(100);
      // Fade out after a short delay
      const t = setTimeout(() => setVisible(false), 600);
      return () => clearTimeout(t);
    }
  }, [scriptStatus]);

  if (!visible) return null;

  const statusText =
    scriptStatus === 'GENERATING'
      ? progress < 30
        ? 'AI 正在分析小说结构…'
        : progress < 55
        ? '正在提取角色与情节…'
        : progress < 75
        ? '正在切分场景…'
        : progress < 90
        ? '正在生成对话与动作…'
        : '正在编排最终剧本…'
      : '正在加载剧本…';

  return (
    <div className="flex flex-col items-center gap-6 animate-fade-in">
      {/* Icon */}
      <div className="relative">
        <div className="h-16 w-16 rounded-2xl bg-teal-500/10 flex items-center justify-center">
          <Loader2 className="h-8 w-8 text-teal-400 animate-spin" />
        </div>
        <div className="absolute inset-0 rounded-2xl ring-2 ring-teal-500/20 animate-glow-pulse" />
      </div>

      {/* Progress bar */}
      <div className="w-80 max-w-[90vw] space-y-2">
        <div className="h-1.5 w-full rounded-full bg-white/5 overflow-hidden">
          <div
            className="h-full rounded-full bg-gradient-to-r from-teal-500 to-cyan-400 transition-all duration-500 ease-out"
            style={{ width: `${progress}%` }}
          />
        </div>
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>{statusText}</span>
          <span className="font-mono tabular-nums text-teal-400/80">
            {Math.round(progress)}%
          </span>
        </div>
      </div>

      {/* Subtle hint */}
      <p className="text-[11px] text-muted-foreground/40">
        剧本生成预计需要 3-5 分钟，请耐心等待
      </p>
    </div>
  );
}

export default function ScriptPage() {
  const params = useParams();
  const scriptId = Number(params.id);
  const { script, setScript, progress, updateProgress, isLoading, setIsLoading } =
    useScriptStore();
  const [generationError, setGenerationError] = useState<string | null>(null);

  // Fetch script on mount
  useEffect(() => {
    setIsLoading(true);
    getScript(scriptId)
      .then(setScript)
      .catch(console.error);
  }, [scriptId, setScript, setIsLoading]);

  // SSE progress — backend sends named events: "progress", "complete", "error"
  const handleProgress = useCallback((data: WorkflowProgressType) => {
    updateProgress(data);
  }, [updateProgress]);

  const handleComplete = useCallback(() => {
    getScript(scriptId).then(setScript).catch(console.error);
  }, [scriptId, setScript]);

  const handleError = useCallback(() => {
    getScript(scriptId).then(setScript).catch(console.error);
    setGenerationError('剧本生成失败，请返回重新生成');
  }, [scriptId, setScript]);

  useSSE({
    scriptId,
    enabled: script?.status === 'GENERATING' && !generationError,
    onProgress: handleProgress,
    onComplete: handleComplete,
    onError: handleError,
  });

  const { error: storeError } = useScriptStore();

  const sseProgress = progress?.overallProgress ?? 0;

  // Error state
  if (storeError && !script) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="flex flex-col items-center gap-4 max-w-md text-center animate-fade-in">
          <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-red-500/10">
            <AlertCircle className="h-6 w-6 text-red-400" />
          </div>
          <p className="text-sm font-medium">加载剧本失败</p>
          <p className="text-xs text-muted-foreground">{storeError}</p>
          <Link href="/scripts" className="text-xs text-teal-400 hover:underline">
            ← 返回剧本列表
          </Link>
        </div>
      </div>
    );
  }

  // Loading / Generating state — show progress bar
  if (isLoading || !script) {
    return (
      <div className="flex items-center justify-center h-screen">
        <LoadingProgress
          scriptStatus={script?.status}
          sseProgress={sseProgress}
        />
      </div>
    );
  }

  // Show progress bar overlay when generating
  const showProgressOverlay = script.status === 'GENERATING' && !generationError;

  return (
    <div className="flex flex-col h-screen">
      {/* Top nav bar */}
      <header className="glass border-b border-white/5 px-6 h-12 flex items-center shrink-0 z-20">
        <div className="flex items-center gap-4 w-full max-w-full">
          <Link
            href="/"
            className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors shrink-0"
          >
            <FileText className="h-4 w-4 text-teal-400" />
            <span className="font-medium text-foreground">Novel2Script</span>
          </Link>
          <span className="text-muted-foreground/30">/</span>
          <nav className="flex items-center gap-1 text-sm">
            <Link
              href={`/scripts/${scriptId}`}
              className="px-2 py-1 rounded-md bg-teal-500/10 text-teal-400 text-xs font-medium"
            >
              剧本编辑
            </Link>
            <Link
              href={`/scripts/${scriptId}/characters`}
              className="px-2 py-1 rounded-md hover:bg-white/5 text-muted-foreground hover:text-foreground text-xs transition-colors"
            >
              人物管理
            </Link>
            <Link
              href={`/scripts/${scriptId}/yaml`}
              className="px-2 py-1 rounded-md hover:bg-white/5 text-muted-foreground hover:text-foreground text-xs transition-colors"
            >
              YAML 预览
            </Link>
          </nav>
          <div className="ml-auto flex items-center gap-2 text-xs text-muted-foreground">
            <span>{script.title}</span>
          </div>
        </div>
      </header>

      {/* Generation error banner */}
      {generationError && (
        <div className="bg-red-500/10 border-b border-red-500/20 px-6 py-3">
          <div className="flex items-center gap-2 max-w-3xl mx-auto">
            <AlertCircle className="h-4 w-4 text-red-400 shrink-0" />
            <p className="text-sm text-red-400">{generationError}</p>
            <Link
              href="/scripts/new"
              className="ml-auto text-xs px-3 py-1 rounded-md bg-red-500/20 text-red-400 hover:bg-red-500/30 transition-colors shrink-0"
            >
              重新生成
            </Link>
          </div>
        </div>
      )}

      {/* Script failed status banner (already failed when loading) */}
      {!generationError && script.status === 'FAILED' && (
        <div className="bg-red-500/10 border-b border-red-500/20 px-6 py-3">
          <div className="flex items-start gap-2 max-w-3xl mx-auto">
            <AlertCircle className="h-4 w-4 text-red-400 shrink-0 mt-0.5" />
            <div className="flex-1 min-w-0">
              <p className="text-sm text-red-400 font-medium">剧本生成失败</p>
              {(script as any).workflowState?.error && (
                <p className="text-xs text-red-400/70 mt-0.5 break-all">
                  {(script as any).workflowState.error}
                </p>
              )}
            </div>
            <Link
              href="/scripts/new"
              className="ml-auto text-xs px-3 py-1 rounded-md bg-red-500/20 text-red-400 hover:bg-red-500/30 transition-colors shrink-0"
            >
              重新生成
            </Link>
          </div>
        </div>
      )}

      {/* Progress overlay when generating */}
      {showProgressOverlay && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <LoadingProgress
            scriptStatus={script.status}
            sseProgress={sseProgress}
          />
        </div>
      )}

      <div className="flex-1 overflow-hidden">
        <ScriptEditor scriptId={scriptId} />
      </div>
    </div>
  );
}
