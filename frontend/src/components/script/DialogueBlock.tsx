'use client';

import { useState } from 'react';
import { useScriptStore } from '@/stores/script-store';
import { emotionLabel, cn } from '@/lib/utils';
import { Pencil, Check, X } from 'lucide-react';
import type { Dialogue } from '@/types/script';

interface DialogueBlockProps {
  dialogue: Dialogue;
}

export function DialogueBlock({ dialogue }: DialogueBlockProps) {
  const { selectedSceneId, updateDialogue } = useScriptStore();
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(dialogue.content);
  const isSelected = selectedSceneId === dialogue.sceneId;

  const handleSave = () => {
    updateDialogue(dialogue.id, { content });
    setEditing(false);
  };

  const handleCancel = () => {
    setContent(dialogue.content);
    setEditing(false);
  };

  return (
    <div
      className={cn(
        'group/dialogue relative text-sm leading-relaxed py-1.5 px-2 -mx-2 rounded-lg transition-colors',
        selectedSceneId === dialogue.sceneId && 'hover:bg-white/[0.03]'
      )}
    >
      <div className="flex items-baseline gap-2">
        {/* Speaker */}
        <span className="font-semibold text-teal-400 shrink-0 min-w-[4rem]">
          {dialogue.speaker}
        </span>

        {/* Emotion badge */}
        {dialogue.emotion && (
          <span className="text-muted-foreground text-xs shrink-0">
            ({emotionLabel(dialogue.emotion)})
          </span>
        )}

        {/* Parenthetical */}
        {dialogue.parenthetical && (
          <span className="text-muted-foreground/60 text-xs shrink-0 italic">
            {dialogue.parenthetical}
          </span>
        )}

        {/* Content */}
        {editing ? (
          <div className="flex-1 flex items-center gap-1.5">
            <input
              className="flex-1 bg-white/10 border border-white/10 rounded px-2 py-0.5 text-sm focus:outline-none focus:border-teal-500/40"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              autoFocus
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSave();
                if (e.key === 'Escape') handleCancel();
              }}
            />
            <button
              onClick={handleSave}
              className="p-0.5 rounded hover:bg-teal-500/20 text-teal-400"
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
        ) : (
          <>
            <span className="text-foreground/90">{dialogue.content}</span>
            {/* Edit button on hover */}
            <button
              onClick={() => setEditing(true)}
              className="opacity-0 group-hover/dialogue:opacity-100 transition-opacity p-0.5 rounded hover:bg-white/10 text-muted-foreground hover:text-foreground ml-auto shrink-0"
              title="编辑对白"
            >
              <Pencil className="h-3 w-3" />
            </button>
          </>
        )}
      </div>

      {/* Reply indicator */}
      {dialogue.replyTo && (
        <div className="ml-[4rem] mt-0.5">
          <span className="text-[10px] text-muted-foreground/50 italic">
            ↳ 回复 #{dialogue.replyTo}
          </span>
        </div>
      )}
    </div>
  );
}
