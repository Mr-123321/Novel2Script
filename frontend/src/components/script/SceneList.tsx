'use client';

import { useScriptStore } from '@/stores/script-store';
import { sceneHeader } from '@/lib/utils';
import { cn } from '@/lib/utils';
import { Plus, Hash } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { Scene } from '@/types/script';

interface SceneListProps {
  scenes: Scene[];
}

export function SceneList({ scenes }: SceneListProps) {
  const { selectedSceneId, selectScene } = useScriptStore();

  return (
    <div className="p-3 space-y-1.5">
      <div className="flex items-center justify-between px-2 py-1.5">
        <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
          场景列表
        </h3>
        <span className="text-[10px] text-muted-foreground bg-white/5 px-1.5 py-0.5 rounded-md font-medium">
          {scenes.length}
        </span>
      </div>

      {scenes.map((scene) => (
        <button
          key={scene.id}
          onClick={() => selectScene(scene.id)}
          className={cn(
            'w-full text-left rounded-lg p-2.5 text-sm transition-all duration-200',
            selectedSceneId === scene.id
              ? 'bg-teal-500/10 text-teal-400 ring-1 ring-teal-500/20'
              : 'hover:bg-white/5 text-muted-foreground hover:text-foreground'
          )}
        >
          <div className="flex items-center gap-2 font-medium">
            <Hash className="h-3 w-3 shrink-0 opacity-50" />
            <span className="truncate">Scene {scene.sceneNumber}</span>
          </div>
          <div className="text-[11px] text-muted-foreground truncate mt-1 ml-5">
            {sceneHeader(scene)}
          </div>
        </button>
      ))}

      <Button
        variant="ghost"
        size="sm"
        className="w-full justify-start text-muted-foreground mt-2 h-8 text-xs"
      >
        <Plus className="h-3.5 w-3.5 mr-1.5" />
        添加场景
      </Button>
    </div>
  );
}
