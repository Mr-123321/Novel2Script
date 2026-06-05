'use client';

import { Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useScriptStore } from '@/stores/script-store';
import { generateScript } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import type { SelectOption } from '@/components/ui/select';
import { SlidersHorizontal, Wand2, ArrowLeft, Loader2, AlertCircle } from 'lucide-react';
import Link from 'next/link';

function NewScriptForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const novelId = searchParams.get('novelId');
  const { setError: setStoreError } = useScriptStore();

  const [maxScenes, setMaxScenes] = useState(20);
  const [style, setStyle] = useState('');
  const [focusCharacters, setFocusCharacters] = useState('');
  const [generating, setGenerating] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  const STYLE_OPTIONS: SelectOption[] = [
    { value: '标准', label: '标准' },
    { value: '悬疑', label: '悬疑' },
    { value: '轻喜剧', label: '轻喜剧' },
    { value: '正剧', label: '正剧' },
    { value: '史诗', label: '史诗' },
    { value: '文艺', label: '文艺' },
    { value: '黑暗', label: '黑暗' },
    { value: '动作', label: '动作' },
    { value: '爱情', label: '爱情' },
    { value: '科幻', label: '科幻' },
  ];

  const handleGenerate = async () => {
    if (!novelId) return;
    setGenerating(true);
    setLocalError(null);
    setStoreError(null);

    try {
      const result = await generateScript({
        novelId: Number(novelId),
        maxScenes,
        style: style || undefined,
        focusCharacters: focusCharacters || undefined,
      });
      // Backend returns {executionId, status, message}
      router.push(`/scripts/${result.executionId}`);
    } catch (err: unknown) {
      // ApiError has detail field; Error has message field
      const message =
        (err as { detail?: string }).detail ??
        (err instanceof Error ? err.message : '生成失败，请检查后端服务是否启动');
      setLocalError(message);
      setStoreError(message);
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div className="max-w-xl mx-auto px-6 py-16 animate-fade-in">
      {/* Back link */}
      <Link
        href="/"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors mb-8"
      >
        <ArrowLeft className="h-4 w-4" />
        返回首页
      </Link>

      {/* Header */}
      <div className="mb-8">
        <div className="inline-flex items-center gap-2.5 mb-3">
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-teal-500/10 ring-1 ring-teal-500/20">
            <SlidersHorizontal className="h-4.5 w-4.5 text-teal-400" />
          </span>
          <h1 className="text-2xl font-bold">剧本生成配置</h1>
        </div>
        <p className="text-muted-foreground text-sm ml-12">
          设置生成参数，AI 将根据配置自动生成剧本
        </p>
      </div>

      {/* Form */}
      <div className="space-y-6 ml-12">
        <div className="rounded-xl border border-white/5 bg-white/[0.02] p-5 space-y-5">
          <div>
            <Label htmlFor="maxScenes" className="mb-1.5 block">
              最大场景数
            </Label>
            <Input
              id="maxScenes"
              type="number"
              min={1}
              max={100}
              value={maxScenes}
              onChange={(e) => setMaxScenes(Number(e.target.value))}
            />
            <p className="text-[11px] text-muted-foreground mt-1.5">
              范围 1–100，默认 20。场景越多，剧本越详细
            </p>
          </div>

          <div>
            <Label htmlFor="style" className="mb-1.5 block">
              剧本风格
              <span className="text-muted-foreground font-normal ml-1">(可选)</span>
            </Label>
            <Select
              id="style"
              placeholder="选择剧本风格（默认：标准）"
              options={STYLE_OPTIONS}
              value={style}
              onChange={setStyle}
            />
          </div>

          <div>
            <Label htmlFor="focusCharacters" className="mb-1.5 block">
              重点关注角色
              <span className="text-muted-foreground font-normal ml-1">(可选)</span>
            </Label>
            <Input
              id="focusCharacters"
              placeholder="如：林川, 李雪"
              value={focusCharacters}
              onChange={(e) => setFocusCharacters(e.target.value)}
            />
            <p className="text-[11px] text-muted-foreground mt-1.5">
              多个角色用逗号分隔，留空则自动识别
            </p>
          </div>
        </div>

        {localError && (
          <div className="flex items-center gap-2.5 text-sm text-red-400 bg-red-500/10 rounded-xl p-3.5 border border-red-500/20 animate-scale-in">
            <AlertCircle className="h-4 w-4 shrink-0" />
            {localError}
          </div>
        )}

        <Button
          onClick={handleGenerate}
          disabled={!novelId || generating}
          variant="gradient"
          size="lg"
          className="w-full"
        >
          {generating ? (
            <>
              <div className="h-4 w-4 rounded-full border-2 border-teal-950/30 border-t-teal-950 animate-spin" />
              提交中...
            </>
          ) : (
            <>
              <Wand2 className="h-4 w-4" />
              开始生成剧本
            </>
          )}
        </Button>
      </div>
    </div>
  );
}

export default function NewScriptPage() {
  return (
    <Suspense
      fallback={
        <div className="flex items-center justify-center h-screen">
          <div className="flex flex-col items-center gap-4">
            <Loader2 className="h-8 w-8 animate-spin text-teal-400" />
            <p className="text-sm text-muted-foreground">加载中...</p>
          </div>
        </div>
      }
    >
      <NewScriptForm />
    </Suspense>
  );
}
