'use client';

import { useEffect } from 'react';
import { useParams } from 'next/navigation';
import { useScriptStore } from '@/stores/script-store';
import { useSSE } from '@/hooks/use-sse';
import { getScript } from '@/lib/api';
import { ScriptEditor } from '@/components/script/ScriptEditor';
import { WorkflowProgress } from '@/components/script/WorkflowProgress';
import type { WorkflowProgress as WorkflowProgressType } from '@/types/script';
import Link from 'next/link';
import { FileText } from 'lucide-react';

export default function ScriptPage() {
  const params = useParams();
  const scriptId = Number(params.id);
  const { script, setScript, updateProgress, isLoading } = useScriptStore();

  useEffect(() => {
    getScript(scriptId).then(setScript).catch(console.error);
  }, [scriptId, setScript]);

  const handleProgress = (data: WorkflowProgressType) => {
    updateProgress(data);
    if (data.progress >= 100) {
      getScript(scriptId).then(setScript).catch(console.error);
    }
  };

  useSSE({
    scriptId,
    enabled: script?.status === 'GENERATING',
    onMessage: handleProgress,
    onError: (event) => console.error('SSE error:', event),
  });

  if (isLoading || !script) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="flex flex-col items-center gap-4 animate-fade-in">
          <div className="relative">
            <div className="h-12 w-12 rounded-2xl bg-teal-500/10 flex items-center justify-center">
              <div className="h-6 w-6 rounded-full border-2 border-teal-500/30 border-t-teal-400 animate-spin" />
            </div>
            <div className="absolute inset-0 rounded-2xl ring-2 ring-teal-500/20 animate-glow-pulse" />
          </div>
          <p className="text-sm text-muted-foreground">加载剧本中...</p>
        </div>
      </div>
    );
  }

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
        </div>
      </header>

      <WorkflowProgress progress={script.progress} />
      <div className="flex-1 overflow-hidden">
        <ScriptEditor scriptId={scriptId} />
      </div>
    </div>
  );
}

export const runtime = 'edge';
