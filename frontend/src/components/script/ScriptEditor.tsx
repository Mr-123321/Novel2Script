'use client';

import { useScriptStore } from '@/stores/script-store';
import { SceneList } from './SceneList';
import { SceneCard } from './SceneCard';
import { CharacterPanel } from './CharacterPanel';
import { PanelLeft, PanelRight } from 'lucide-react';
import { useState } from 'react';

interface ScriptEditorProps {
  scriptId: number;
}

export function ScriptEditor({ scriptId: _scriptId }: ScriptEditorProps) {
  const { script, selectedSceneId } = useScriptStore();
  const [leftOpen, setLeftOpen] = useState(true);
  const [rightOpen, setRightOpen] = useState(true);

  const selectedScene = script?.scenes.find((s) => s.id === selectedSceneId);

  return (
    <div className="flex h-[calc(100vh-6rem)]">
      {/* Left sidebar: Scene list */}
      <aside
        className={`${
          leftOpen ? 'w-64' : 'w-0'
        } border-r border-white/5 bg-white/[0.02] overflow-hidden transition-all duration-300 shrink-0`}
      >
        <SceneList scenes={script?.scenes ?? []} />
      </aside>

      {/* Center: Script content */}
      <main className="flex-1 overflow-y-auto">
        {/* Toggle buttons */}
        <div className="sticky top-0 z-10 flex items-center justify-between px-3 py-2 glass border-b border-white/5">
          <button
            onClick={() => setLeftOpen(!leftOpen)}
            className="p-1.5 rounded-md hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors"
            title="切换场景列表"
          >
            <PanelLeft className="h-4 w-4" />
          </button>
          <span className="text-xs text-muted-foreground font-medium">
            {script?.title ?? '剧本编辑'}
          </span>
          <button
            onClick={() => setRightOpen(!rightOpen)}
            className="p-1.5 rounded-md hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors"
            title="切换角色面板"
          >
            <PanelRight className="h-4 w-4" />
          </button>
        </div>

        <div className="max-w-3xl mx-auto py-8 px-6 space-y-6">
          {script?.scenes.map((scene) => (
            <SceneCard key={scene.id} scene={scene} />
          ))}
          {(!script?.scenes || script.scenes.length === 0) && (
            <div className="text-center py-16">
              <div className="inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-white/5 mb-4">
                <div className="h-8 w-8 rounded-full border-2 border-teal-500/30 border-t-teal-400 animate-spin" />
              </div>
              <p className="text-muted-foreground">暂无场景，请等待 AI 生成完成</p>
            </div>
          )}
        </div>
      </main>

      {/* Right sidebar: Character panel */}
      <aside
        className={`${
          rightOpen ? 'w-80' : 'w-0'
        } border-l border-white/5 bg-white/[0.02] overflow-hidden transition-all duration-300 shrink-0`}
      >
        <CharacterPanel
          characters={script?.characters ?? []}
          selectedScene={selectedScene ?? null}
        />
      </aside>
    </div>
  );
}
