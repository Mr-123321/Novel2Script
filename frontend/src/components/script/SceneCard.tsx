'use client';

import { useScriptStore } from '@/stores/script-store';
import { sceneHeader, cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { DialogueBlock } from './DialogueBlock';
import { ActionBlock } from './ActionBlock';
import { MapPin, Clock, MessageSquare, Users } from 'lucide-react';
import type { Scene, Dialogue, Action } from '@/types/script';
import type { Character } from '@/types/character';

interface SceneCardProps {
  scene: Scene;
  selected?: boolean;
}

/** Sorted combined content items */
function getSortedContent(scene: Scene): Array<
  { type: 'action'; item: Action } | { type: 'dialogue'; item: Dialogue }
> {
  const items: Array<
    { type: 'action'; item: Action } | { type: 'dialogue'; item: Dialogue }
  > = [
    ...scene.actions.map((a) => ({ type: 'action' as const, item: a })),
    ...scene.dialogues.map((d) => ({ type: 'dialogue' as const, item: d })),
  ];
  items.sort((a, b) => a.item.sequence - b.item.sequence);
  return items;
}

export function SceneCard({ scene, selected }: SceneCardProps) {
  const { selectedSceneId, selectScene, script } = useScriptStore();
  const isSelected = selected ?? (selectedSceneId === scene.id);

  const contentItems = getSortedContent(scene);

  // Map characterIds to character names
  const characterMap = new Map(
    (script?.characters ?? []).map((c) => [c.id, c] as const)
  );
  const sceneCharacters = (scene.characterIds ?? [])
    .map((id) => characterMap.get(id))
    .filter((c) => c !== undefined);

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
          <div className="flex items-center flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground mt-2 ml-8">
            <span className="flex items-center gap-1.5">
              <MapPin className="h-3 w-3" />
              <span className="truncate max-w-[240px]">
                {scene.sceneHeading ?? sceneHeader(scene)}
              </span>
            </span>
            <span className="flex items-center gap-1.5">
              <Clock className="h-3 w-3" /> {scene.timeOfDay}
            </span>
            <span className="flex items-center gap-1.5">
              <MessageSquare className="h-3 w-3" /> {scene.dialogues.length}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {scene.mood && (
            <Badge variant="secondary" className="text-[11px]">
              {scene.mood}
            </Badge>
          )}
        </div>
      </div>

      {/* Characters in this scene */}
      {sceneCharacters.length > 0 && (
        <div className="flex items-center gap-1.5 mb-3 ml-8 flex-wrap">
          <Users className="h-3 w-3 text-muted-foreground shrink-0" />
          {sceneCharacters.map((c) => (
            <span
              key={c.id}
              className="inline-flex text-[11px] px-1.5 py-0.5 rounded-md bg-teal-500/10 text-teal-400 font-medium"
            >
              {c.canonicalName}
            </span>
          ))}
        </div>
      )}

      {/* Summary */}
      {scene.summary && (
        <p className="text-sm text-muted-foreground mb-4 ml-8 leading-relaxed">
          {scene.summary}
        </p>
      )}

      {/* Actions & Dialogues — sorted by sequence */}
      <div className="space-y-1 ml-8">
        {contentItems.map(({ type, item }) =>
          type === 'action' ? (
            <ActionBlock key={`action-${item.id}`} action={item} />
          ) : (
            <DialogueBlock key={`dialogue-${item.id}`} dialogue={item} />
          )
        )}
      </div>

      {/* Selected indicator line */}
      {isSelected && (
        <div className="absolute left-0 top-4 bottom-4 w-0.5 rounded-full bg-gradient-to-b from-teal-400 to-cyan-500" />
      )}
    </div>
  );
}
