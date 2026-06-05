'use client';

import { useState } from 'react';
import { useScriptStore } from '@/stores/script-store';
import { cn } from '@/lib/utils';
import { Pencil, Check, X, Move } from 'lucide-react';
import type { Action } from '@/types/script';

interface ActionBlockProps {
  action: Action;
}

const actionTypeLabel = (type: string): string => {
  const labels: Record<string, string> = {
    ACTION: '动作',
    REACTION: '反应',
    BEAT: '节拍',
    BUSINESS: '调度',
  };
  return labels[type] ?? type;
};

export function ActionBlock({ action }: ActionBlockProps) {
  const { updateDialogue, script } = useScriptStore();
  const [editing, setEditing] = useState(false);
  const [description, setDescription] = useState(action.description);

  const handleSave = () => {
    // Actions live inside scenes alongside dialogues
    const scene = script?.scenes.find((s) => s.id === action.sceneId);
    if (scene) {
      const updatedActions = scene.actions.map((a) =>
        a.id === action.id ? { ...a, description } : a
      );
      useScriptStore.getState().updateScene(action.sceneId, {
        actions: updatedActions as unknown as typeof scene.actions,
      } as Partial<typeof scene>);
    }
    setEditing(false);
  };

  const handleCancel = () => {
    setDescription(action.description);
    setEditing(false);
  };

  return (
    <div
      className={cn(
        'group/action relative text-sm py-1.5 px-2 -mx-2 rounded-lg transition-colors',
        'hover:bg-white/[0.02]'
      )}
    >
      <div className="flex items-start gap-2">
        {/* Action type tag */}
        <span className="inline-flex items-center gap-1 text-[10px] font-medium text-muted-foreground/70 bg-white/5 rounded px-1.5 py-0.5 shrink-0 uppercase tracking-wider">
          <Move className="h-2.5 w-2.5" />
          {actionTypeLabel(action.actionType)}
        </span>

        {editing ? (
          <div className="flex-1 flex items-start gap-1.5">
            <textarea
              className="flex-1 bg-white/10 border border-white/10 rounded px-2 py-1 text-sm focus:outline-none focus:border-teal-500/40 resize-none min-h-[2rem]"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              autoFocus
              rows={2}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && e.ctrlKey) handleSave();
                if (e.key === 'Escape') handleCancel();
              }}
            />
            <div className="flex items-center gap-0.5 shrink-0">
              <button
                onClick={handleSave}
                className="p-0.5 rounded hover:bg-teal-500/20 text-teal-400"
                title="保存 (Ctrl+Enter)"
              >
                <Check className="h-3.5 w-3.5" />
              </button>
              <button
                onClick={handleCancel}
                className="p-0.5 rounded hover:bg-red-500/20 text-red-400"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        ) : (
          <>
            <span className="text-muted-foreground/70 italic flex-1">
              {action.description}
            </span>
            <button
              onClick={() => setEditing(true)}
              className="opacity-0 group-hover/action:opacity-100 transition-opacity p-0.5 rounded hover:bg-white/10 text-muted-foreground hover:text-foreground shrink-0"
              title="编辑动作"
            >
              <Pencil className="h-3 w-3" />
            </button>
          </>
        )}
      </div>

      {/* Duration indicator */}
      {action.durationMs && (
        <div className="ml-[4.5rem] mt-0.5">
          <span className="text-[10px] text-muted-foreground/40">
            ~{(action.durationMs / 1000).toFixed(1)}s
          </span>
        </div>
      )}
    </div>
  );
}
