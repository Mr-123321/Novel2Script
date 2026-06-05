'use client';

import { useScriptStore } from '@/stores/script-store';
import { sceneHeader, cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { MapPin, Clock, MessageSquare } from 'lucide-react';
import type { Scene } from '@/types/script';

interface SceneCardProps {
  scene: Scene;
}

export function SceneCard({ scene }: SceneCardProps) {
  const { selectedSceneId, selectScene } = useScriptStore();
  const isSelected = selectedSceneId === scene.id;

  return (
    <div
      onClick={() => selectScene(scene.id)}
      className={cn(
        'relative rounded-xl border p-5 cursor-pointer transition-all duration-300 card-lift',
        isSelected
          ? 'border-teal-500/30 bg-teal-500/[0.04] shadow-[0_0_30px_rgba(45,212,191,0.06)]'
          : 'border-white/5 bg-white/[0.02] hover:border-white/10 hover:bg-white/[0.04]'
      )}
    >
      {/* Scene Header */}
      <div className="flex items-start justify-between mb-4">
        <div className="min-w-0">
          <h3 className="font-semibold text-sm flex items-center gap-2">
            <span className="inline-flex items-center justify-center h-6 w-6 rounded-md bg-teal-500/10 text-teal-400 text-xs font-bold shrink-0">
              {scene.sceneNumber}
            </span>
            {scene.title && (
              <span className="text-muted-foreground font-normal truncate">
                {scene.title}
              </span>
            )}
          </h3>
          <div className="flex items-center gap-4 text-xs text-muted-foreground mt-2 ml-8">
            <span className="flex items-center gap-1.5">
              <MapPin className="h-3 w-3" />
              <span className="truncate max-w-[200px]">{sceneHeader(scene)}</span>
            </span>
            <span className="flex items-center gap-1.5">
              <Clock className="h-3 w-3" /> {scene.timeOfDay}
            </span>
            <span className="flex items-center gap-1.5">
              <MessageSquare className="h-3 w-3" /> {scene.dialogues.length}
            </span>
          </div>
        </div>
        {scene.mood && (
          <Badge variant="secondary" className="text-[11px] shrink-0">
            {scene.mood}
          </Badge>
        )}
      </div>

      {/* Summary */}
      {scene.summary && (
        <p className="text-sm text-muted-foreground mb-4 ml-8 leading-relaxed">
          {scene.summary}
        </p>
      )}

      {/* Actions & Dialogues */}
      <div className="space-y-3 ml-8">
        {[...scene.actions, ...scene.dialogues]
          .sort((a, b) => a.sequence - b.sequence)
          .map((item) => {
            if ('actionType' in item) {
              return (
                <p
                  key={`action-${item.id}`}
                  className="text-sm text-muted-foreground/70 italic leading-relaxed"
                >
                  [{item.description}]
                </p>
              );
            }
            return (
              <div
                key={`dialogue-${item.id}`}
                className="text-sm leading-relaxed group/dialogue"
              >
                <span className="font-semibold text-teal-400">
                  {item.speaker}
                </span>
                {item.emotion && (
                  <span className="text-muted-foreground text-xs ml-1.5">
                    ({item.emotion})
                  </span>
                )}
                {item.parenthetical && (
                  <span className="text-muted-foreground/60 text-xs ml-1">
                    {item.parenthetical}
                  </span>
                )}
                <span className="mx-1.5 text-muted-foreground/30">:</span>
                <span>{item.content}</span>
              </div>
            );
          })}
      </div>

      {/* Selected indicator line */}
      {isSelected && (
        <div className="absolute left-0 top-4 bottom-4 w-0.5 rounded-full bg-gradient-to-b from-teal-400 to-cyan-500" />
      )}
    </div>
  );
}
