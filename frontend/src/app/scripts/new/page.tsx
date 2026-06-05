'use client';

import { Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useScriptStore } from '@/stores/script-store';
import { generateScript } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { SlidersHorizontal, Wand2, ArrowLeft, Loader2 } from 'lucide-react';
import Link from 'next/link';

function NewScriptForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const novelId = searchParams.get('novelId');
  const { setScript, setError } = useScriptStore();

  const [maxScenes, setMaxScenes] = useState(20);
  const [style, setStyle] = useState('');
  const [focusCharacters, setFocusCharacters] = useState('');
  const [generating, setGenerating] = useState(false);

  const handleGenerate = async () => {
    if (!novelId) return;
    setGenerating(true);
    setError(null);

    try {
      const script = await generateScript({
        novelId: Number(novelId),
        maxScenes,
        style: style || undefined,
        focusCharacters: focusCharacters
          ? focusCharacters.split(',').map((s) => s.trim())
          : undefined,
      });
      setScript(script);
      router.push(`/scripts/${script.id}`);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '生成失败，请重试';
      setError(message);
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
              max={50}
              value={maxScenes}
              onChange={(e) => setMaxScenes(Number(e.target.value))}
            />
            <p className="text-[11px] text-muted-foreground mt-1.5">
              范围 1–50，默认 20。场景越多，剧本越详细
            </p>
          </div>

          <div>
            <Label htmlFor="style" className="mb-1.5 block">
              剧本风格
              <span className="text-muted-foreground font-normal ml-1">(可选)</span>
            </Label>
            <Input
              id="style"
              placeholder="如：悬疑、轻喜剧、正剧..."
              value={style}
              onChange={(e) => setStyle(e.target.value)}
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
