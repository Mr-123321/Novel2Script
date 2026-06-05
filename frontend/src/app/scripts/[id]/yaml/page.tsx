'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import dynamic from 'next/dynamic';
import { getScriptYaml, downloadScriptYaml } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Download, Copy, Check, FileCode, Loader2, AlertCircle } from 'lucide-react';
import Link from 'next/link';

const Editor = dynamic(() => import('@monaco-editor/react'), { ssr: false });

export default function YamlPage() {
  const params = useParams();
  const scriptId = Number(params.id);
  const [yaml, setYaml] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    setLoading(true);
    getScriptYaml(scriptId)
      .then((result) => {
        if ('yaml' in result && result.yaml) {
          setYaml(result.yaml);
        } else if ('message' in result && result.message) {
          setError(result.message);
        }
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : '加载失败');
      })
      .finally(() => setLoading(false));
  }, [scriptId]);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(yaml);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [yaml]);

  const handleDownload = useCallback(async () => {
    try {
      const blob = await downloadScriptYaml(scriptId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `script-${scriptId}.yaml`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      // Fallback: download from already loaded yaml text
      const blob = new Blob([yaml], { type: 'application/x-yaml' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `script-${scriptId}.yaml`;
      a.click();
      URL.revokeObjectURL(url);
    }
  }, [scriptId, yaml]);

  return (
    <div className="flex flex-col h-screen">
      {/* Header */}
      <header className="glass border-b border-white/5 px-6 h-12 flex items-center justify-between shrink-0 z-20">
        <div className="flex items-center gap-4">
          <Link
            href={`/scripts/${scriptId}`}
            className="text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            ← 返回剧本
          </Link>
          <span className="text-white/10">|</span>
          <div className="flex items-center gap-2">
            <FileCode className="h-4 w-4 text-teal-400" />
            <h1 className="font-semibold text-sm">YAML 预览</h1>
          </div>
        </div>
        {!loading && !error && (
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={handleCopy}>
              {copied ? (
                <Check className="h-3.5 w-3.5" />
              ) : (
                <Copy className="h-3.5 w-3.5" />
              )}
              <span className="ml-1">{copied ? '已复制' : '复制'}</span>
            </Button>
            <Button size="sm" onClick={handleDownload}>
              <Download className="h-3.5 w-3.5" />
              <span className="ml-1">下载</span>
            </Button>
          </div>
        )}
      </header>

      {/* Editor */}
      <main className="flex-1">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <div className="flex flex-col items-center gap-3">
              <Loader2 className="h-8 w-8 animate-spin text-teal-400" />
              <p className="text-sm text-muted-foreground">加载 YAML...</p>
            </div>
          </div>
        ) : error ? (
          <div className="flex items-center justify-center h-full">
            <div className="flex flex-col items-center gap-3 text-center max-w-md">
              <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-500/10">
                <AlertCircle className="h-6 w-6 text-amber-400" />
              </div>
              <p className="text-sm font-medium text-foreground">暂无可用的 YAML</p>
              <p className="text-xs text-muted-foreground">{error}</p>
              <Button variant="outline" size="sm" onClick={() => window.location.reload()}>
                重新加载
              </Button>
            </div>
          </div>
        ) : (
          <Editor
            language="yaml"
            theme="vs-dark"
            value={yaml}
            options={{
              readOnly: true,
              fontSize: 14,
              fontFamily: 'var(--font-geist-mono), monospace',
              minimap: { enabled: false },
              padding: { top: 16, bottom: 16 },
              lineNumbers: 'on',
              renderLineHighlight: 'line',
              smoothScrolling: true,
              cursorBlinking: 'smooth',
            }}
            loading={
              <div className="flex items-center justify-center h-full">
                <Loader2 className="h-8 w-8 animate-spin text-teal-400" />
              </div>
            }
          />
        )}
      </main>
    </div>
  );
}
