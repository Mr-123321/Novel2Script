'use client';

import { useCallback, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useDropzone } from 'react-dropzone';
import { Upload, FileText, AlertCircle, Loader2, ArrowUpCircle } from 'lucide-react';
import { cn } from '@/lib/utils';
import { uploadNovel } from '@/lib/api';

export function UploadZone() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  const onDrop = useCallback(
    async (acceptedFiles: File[]) => {
      const file = acceptedFiles[0];
      if (!file) return;

      setError(null);
      setUploading(true);

      try {
        const data = await uploadNovel(file);
        router.push(`/scripts/new?novelId=${data.novelId}`);
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : '上传失败，请重试';
        setError(message);
      } finally {
        setUploading(false);
      }
    },
    [router]
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'text/plain': ['.txt'],
      'text/markdown': ['.md'],
    },
    maxSize: 50 * 1024 * 1024,
    multiple: false,
  });

  return (
    <div className="space-y-3">
      <div
        {...getRootProps()}
        className={cn(
          'relative rounded-xl p-12 text-center cursor-pointer transition-all duration-300',
          'border-2 border-dashed',
          isDragActive
            ? 'border-teal-400 bg-teal-500/10 scale-[1.02] shadow-[0_0_40px_rgba(45,212,191,0.1)]'
            : 'border-white/10 hover:border-white/20 hover:bg-white/[0.02]'
        )}
      >
        <input {...getInputProps()} />
        {uploading ? (
          <div className="flex flex-col items-center gap-4">
            <div className="relative">
              <div className="h-16 w-16 rounded-2xl bg-teal-500/10 flex items-center justify-center">
                <Loader2 className="h-8 w-8 animate-spin text-teal-400" />
              </div>
              <div className="absolute inset-0 rounded-2xl ring-2 ring-teal-500/20 animate-glow-pulse" />
            </div>
            <div>
              <p className="text-base font-medium text-foreground">正在上传解析...</p>
              <p className="text-sm text-muted-foreground mt-1">
                AI 正在分析小说结构与人物关系
              </p>
            </div>
          </div>
        ) : (
          <>
            <div
              className={cn(
                'mx-auto mb-5 h-16 w-16 rounded-2xl flex items-center justify-center transition-all duration-300',
                isDragActive
                  ? 'bg-teal-500/20 text-teal-400 scale-110'
                  : 'bg-white/5 text-muted-foreground group-hover:text-teal-400 group-hover:bg-teal-500/10'
              )}
            >
              {isDragActive ? (
                <ArrowUpCircle className="h-8 w-8" />
              ) : (
                <Upload className="h-8 w-8" />
              )}
            </div>
            <p className="text-base font-medium text-foreground mb-1">
              {isDragActive ? '松开以放置文件' : '拖拽小说文件到此处，或点击上传'}
            </p>
            <p className="text-sm text-muted-foreground">
              支持 .txt / .md 格式 · 最大 50 MB
            </p>
            <div className="flex items-center justify-center gap-3 mt-4">
              <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground px-2.5 py-1 rounded-md bg-white/5 border border-white/5">
                <FileText className="h-3 w-3" /> .txt
              </span>
              <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground px-2.5 py-1 rounded-md bg-white/5 border border-white/5">
                <FileText className="h-3 w-3" /> .md
              </span>
            </div>
          </>
        )}
      </div>

      {error && (
        <div className="flex items-center gap-2.5 text-sm text-red-400 bg-red-500/10 rounded-xl p-3.5 border border-red-500/20 animate-scale-in">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}
    </div>
  );
}
