'use client';

import { useParams } from 'next/navigation';
import dynamic from 'next/dynamic';
import { useScriptStore } from '@/stores/script-store';
import { CharacterCard } from '@/components/character/CharacterCard';
import { useEffect } from 'react';
import { getScript } from '@/lib/api';
import { Users, FileText, FileCode, ArrowLeft } from 'lucide-react';
import Link from 'next/link';

const RelationshipGraph = dynamic(
  () => import('@/components/character/RelationshipGraph').then((m) => m.RelationshipGraph),
  { ssr: false }
);

const CharacterEditor = dynamic(
  () => import('@/components/character/CharacterEditor').then((m) => m.CharacterEditor),
  { ssr: false }
);

export default function CharactersPage() {
  const params = useParams();
  const scriptId = Number(params.id);
  const { script, setScript, selectedCharacterId, isLoading, setIsLoading } =
    useScriptStore();

  useEffect(() => {
    setIsLoading(true);
    getScript(scriptId)
      .then(setScript)
      .catch(console.error);
  }, [scriptId, setScript, setIsLoading]);

  if (isLoading || !script) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="flex flex-col items-center gap-4 animate-fade-in">
          <div className="h-10 w-10 rounded-full border-2 border-teal-500/30 border-t-teal-400 animate-spin" />
          <p className="text-sm text-muted-foreground">加载中...</p>
        </div>
      </div>
    );
  }

  const selectedCharacter = script.characters.find(
    (c) => c.id === selectedCharacterId
  );

  return (
    <div className="flex flex-col h-screen">
      {/* Top nav */}
      <header className="glass border-b border-white/5 px-6 h-12 flex items-center shrink-0 z-20">
        <div className="flex items-center gap-4 w-full">
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
              className="px-2 py-1 rounded-md hover:bg-white/5 text-muted-foreground hover:text-foreground text-xs transition-colors"
            >
              剧本编辑
            </Link>
            <Link
              href={`/scripts/${scriptId}/characters`}
              className="px-2 py-1 rounded-md bg-purple-500/10 text-purple-400 text-xs font-medium"
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

      {/* Content */}
      <main className="flex-1 overflow-y-auto">
        <div className="max-w-6xl mx-auto px-6 py-8 animate-fade-in">
          {/* Header */}
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-2xl font-bold flex items-center gap-3">
                <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-purple-500/10 ring-1 ring-purple-500/20">
                  <Users className="h-4.5 w-4.5 text-purple-400" />
                </span>
                人物管理
              </h1>
              <p className="text-muted-foreground text-sm mt-1.5 ml-12">
                {script.characters.length} 个角色，点击卡片查看详情
              </p>
            </div>
          </div>

          {/* Character Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {script.characters.map((character) => (
              <CharacterCard key={character.id} character={character} />
            ))}
          </div>

          {script.characters.length === 0 && (
            <div className="text-center py-20">
              <div className="inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-white/5 mb-4">
                <Users className="h-7 w-7 text-muted-foreground" />
              </div>
              <p className="text-muted-foreground">暂无角色数据</p>
              <p className="text-xs text-muted-foreground mt-1">
                等待 AI 生成完成后将自动提取角色信息
              </p>
            </div>
          )}

          {/* Relationship graph */}
          {script.characters.length > 0 && (
            <div className="mt-8">
              <RelationshipGraph />
            </div>
          )}

          {selectedCharacter && (
            <CharacterEditor character={selectedCharacter} />
          )}
        </div>
      </main>
    </div>
  );
}
