'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Editor from '@monaco-editor/react';
import { getScriptYaml } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Download, Copy, Check, FileCode, Loader2 } from 'lucide-react';
import Link from 'next/link';

export default function YamlPage() {
  const params = useParams();
  const scriptId = Number(params.id);
  const [yaml, setYaml] = useState('');
  const [loading, setLoading] = useState(true);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    getScriptYaml(scriptId)
      .then(setYaml)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [scriptId]);

  const handleCopy = () => {
    navigator.clipboard.writeText(yaml);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const blob = new Blob([yaml], { type: 'text/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `script-${scriptId}.yaml`;
    a.click();
    URL.revokeObjectURL(url);
  };

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
        {!loading && (
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
