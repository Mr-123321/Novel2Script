'use client';

import { useScriptStore } from '@/stores/script-store';
import { cn } from '@/lib/utils';
import { SceneList } from './SceneList';
import { SceneCard } from './SceneCard';
import { CharacterPanel } from './CharacterPanel';
import { PanelLeft, PanelRight, BookOpen, Edit3, X } from 'lucide-react';
import { useState, useRef, useEffect, useCallback } from 'react';
import { ToastContainer } from '@/components/ui/toast';
import type { PlotInsertion } from '@/types/script';

interface ScriptEditorProps {
  scriptId: number;
}

/** Inline plot insertion block (user-added narrative between scenes) */
function PlotInsertionBlock({
  insertion,
  onDelete,
  editable,
}: {
  insertion: PlotInsertion;
  onDelete: (id: number) => void;
  editable: boolean;
}) {
  return (
    <div className="relative rounded-lg border border-dashed border-amber-500/30 bg-amber-500/[0.03] px-4 py-3 my-3">
      {editable && (
        <button
          onClick={() => onDelete(insertion.id)}
          className="absolute top-2 right-2 p-0.5 rounded hover:bg-white/10 text-muted-foreground hover:text-red-400 transition-colors"
          title="删除此插入"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}
      <div className="flex items-center gap-2 mb-1">
        <span className="text-[10px] uppercase tracking-wider text-amber-500/60 font-medium">
          📝 情节插入
        </span>
      </div>
      <p className="text-sm text-muted-foreground leading-relaxed whitespace-pre-wrap">
        {insertion.text}
      </p>
    </div>
  );
}

export function ScriptEditor({ scriptId: _scriptId }: ScriptEditorProps) {
  const { script, selectedSceneId, setScript } = useScriptStore();
  const [leftOpen, setLeftOpen] = useState(true);
  const [rightOpen, setRightOpen] = useState(true);
  const [editMode, setEditMode] = useState(false);
  const sceneRefs = useRef<Map<number, HTMLDivElement>>(new Map());

  const selectedScene = script?.scenes.find((s) => s.id === selectedSceneId);

  // Scroll to selected scene when it changes
  useEffect(() => {
    if (selectedSceneId) {
      const el = sceneRefs.current.get(selectedSceneId);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  }, [selectedSceneId]);

  const insertions = script?.plotInsertions ?? [];
  const scenes = script?.scenes ?? [];

  const handleDeleteInsertion = useCallback(
    async (insertionId: number) => {
      if (!script) return;
      try {
        await fetch(
          `${process.env.NEXT_PUBLIC_API_URL ?? ''}/api/v1/scripts/${script.id}/plot-insertions/${insertionId}`,
          { method: 'DELETE' }
        );
        const scriptRes = await fetch(
          `${process.env.NEXT_PUBLIC_API_URL ?? ''}/api/v1/scripts/${script.id}`
        );
        if (scriptRes.ok) {
          const updated = await scriptRes.json();
          setScript(updated);
        }
      } catch (err) {
        console.error('Failed to delete plot insertion:', err);
      }
    },
    [script, setScript]
  );

  return (
    <div className="flex h-[calc(100vh-6rem)]">
      <ToastContainer />
      {/* Left sidebar: Scene list */}
      <aside
        className={`${
          leftOpen ? 'w-64' : 'w-0'
        } border-r border-white/5 bg-white/[0.02] overflow-hidden transition-all duration-300 shrink-0`}
      >
        <SceneList scenes={scenes} />
      </aside>

      {/* Center: Script content */}
      <main className="flex-1 overflow-y-auto">
        {/* Top bar: mode toggle + scene list / character panel toggles */}
        <div className="sticky top-0 z-10 flex items-center justify-between px-3 py-2 glass border-b border-white/5">
          <button
            onClick={() => setLeftOpen(!leftOpen)}
            className="p-1.5 rounded-md hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors"
            title="切换场景列表"
          >
            <PanelLeft className="h-4 w-4" />
          </button>

          {/* Mode toggle */}
          <div className="flex items-center gap-1 bg-white/[0.03] rounded-lg p-0.5 border border-white/5">
            <button
              onClick={() => setEditMode(false)}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-medium transition-all duration-200',
                !editMode
                  ? 'bg-teal-500/20 text-teal-300 shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              )}
            >
              <BookOpen className="h-3.5 w-3.5" />
              阅读模式
            </button>
            <button
              onClick={() => setEditMode(true)}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-medium transition-all duration-200',
                editMode
                  ? 'bg-amber-500/20 text-amber-300 shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              )}
            >
              <Edit3 className="h-3.5 w-3.5" />
              编辑剧本
            </button>
          </div>

          <button
            onClick={() => setRightOpen(!rightOpen)}
            className="p-1.5 rounded-md hover:bg-white/10 text-muted-foreground hover:text-foreground transition-colors"
            title="切换角色面板"
          >
            <PanelRight className="h-4 w-4" />
          </button>
        </div>

        <div className="max-w-3xl mx-auto py-8 px-6 space-y-6">
          {/* Insertions before first scene (position 0) */}
          {insertions
            .filter((ins) => ins.position === 0)
            .map((ins) => (
              <PlotInsertionBlock
                key={`ins-${ins.id}`}
                insertion={ins}
                onDelete={handleDeleteInsertion}
                editable={editMode}
              />
            ))}

          {scenes.map((scene, idx) => (
            <div
              key={scene.id}
              ref={(el) => {
                if (el) sceneRefs.current.set(scene.id, el);
                else sceneRefs.current.delete(scene.id);
              }}
            >
              <SceneCard
                scene={scene}
                selected={selectedSceneId === scene.id}
                editable={editMode}
              />
              {/* Insertions after this scene */}
              {insertions
                .filter((ins) => ins.position === idx + 1)
                .map((ins) => (
                  <PlotInsertionBlock
                    key={`ins-${ins.id}`}
                    insertion={ins}
                    onDelete={handleDeleteInsertion}
                    editable={editMode}
                  />
                ))}
            </div>
          ))}

          {scenes.length === 0 && (
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
