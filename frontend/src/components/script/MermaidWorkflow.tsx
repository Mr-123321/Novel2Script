'use client';

import { useEffect, useState } from 'react';
import { getWorkflowMermaid } from '@/lib/api';
import { Loader2, GitBranch, AlertCircle } from 'lucide-react';

interface MermaidWorkflowProps {
  scriptId: number;
}

export function MermaidWorkflow({ scriptId }: MermaidWorkflowProps) {
  const [mermaid, setMermaid] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    getWorkflowMermaid(scriptId)
      .then((data) => setMermaid(data.mermaid))
      .catch((err) =>
        setError(err instanceof Error ? err.message : '加载工作流失败')
      )
      .finally(() => setLoading(false));
  }, [scriptId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-6 w-6 animate-spin text-teal-400" />
          <p className="text-xs text-muted-foreground">加载工作流...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="flex flex-col items-center gap-2 text-center">
          <AlertCircle className="h-5 w-5 text-amber-400" />
          <p className="text-xs text-muted-foreground">{error}</p>
        </div>
      </div>
    );
  }

  if (!mermaid) return null;

  return (
    <div className="rounded-xl border border-white/5 bg-white/[0.02] p-5">
      <div className="flex items-center gap-2 mb-4">
        <div className="h-7 w-7 rounded-lg bg-teal-500/10 flex items-center justify-center">
          <GitBranch className="h-3.5 w-3.5 text-teal-400" />
        </div>
        <h3 className="font-semibold text-sm">生成工作流</h3>
      </div>
      <pre className="text-xs text-muted-foreground font-mono bg-black/20 rounded-lg p-4 overflow-x-auto whitespace-pre-wrap leading-relaxed">
        {mermaid}
      </pre>
    </div>
  );
}
