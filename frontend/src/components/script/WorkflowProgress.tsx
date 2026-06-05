'use client';

import { useScriptStore } from '@/stores/script-store';
import { Sparkles, CheckCircle2 } from 'lucide-react';

interface WorkflowProgressProps {
  progress: number;
}

export function WorkflowProgress({ progress }: WorkflowProgressProps) {
  const { script } = useScriptStore();
  const isActive = script?.status === 'GENERATING';
  const isComplete = progress >= 100;

  if (!isActive && isComplete) return null;

  return (
    <div className="glass border-b border-white/5 px-6 py-3">
      <div className="max-w-3xl mx-auto flex items-center gap-4">
        {/* Status icon */}
        <div
          className={`shrink-0 h-8 w-8 rounded-lg flex items-center justify-center ${
            isActive
              ? 'bg-teal-500/10 text-teal-400'
              : 'bg-teal-500/10 text-teal-400'
          }`}
        >
          {isActive ? (
            <Sparkles className="h-4 w-4 animate-pulse" />
          ) : (
            <CheckCircle2 className="h-4 w-4" />
          )}
        </div>

        {/* Progress bar */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between text-xs mb-1.5">
            <span className={`font-medium ${isActive ? 'text-teal-400' : 'text-muted-foreground'}`}>
              {isActive ? 'AI 正在生成...' : '生成完成'}
            </span>
            <span className="text-muted-foreground font-mono tabular-nums">
              {Math.round(progress)}%
            </span>
          </div>
          <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-700 ease-out"
              style={{
                width: `${progress}%`,
                background:
                  'linear-gradient(90deg, oklch(0.72 0.14 185), oklch(0.65 0.16 200))',
                boxShadow: isActive
                  ? '0 0 12px oklch(0.72 0.14 185 / 0.4)'
                  : 'none',
              }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
