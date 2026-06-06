'use client';

import { useState, useEffect, useRef } from 'react';
import { useScriptStore } from '@/stores/script-store';
import { emotionLabel, cn } from '@/lib/utils';
import { Pencil, Check, X, GripVertical } from 'lucide-react';
import { toast } from '@/stores/toast-store';
import type { Dialogue } from '@/types/script';

interface DialogueBlockProps {
  dialogue: Dialogue;
  /** External edit control — when true, enter edit mode */
  editing?: boolean;
  /** Called when edit mode changes (save/cancel) */
  onEditStateChange?: (editing: boolean) => void;
}

const EMOTIONS = [
  'NEUTRAL',
  'CALM',
  'ANGRY',
  'SAD',
  'HAPPY',
  'SURPRISED',
  'FEARFUL',
] as const;

export function DialogueBlock({
  dialogue,
  editing: externalEditing,
  onEditStateChange,
}: DialogueBlockProps) {
  const script = useScriptStore((s) => s.script);
  const updateDialogueStore = useScriptStore((s) => s.updateDialogue);

  const [internalEditing, setInternalEditing] = useState(false);
  const [speaker, setSpeaker] = useState(dialogue.speaker);
  const [emotion, setEmotion] = useState(dialogue.emotion ?? 'NEUTRAL');
  const [content, setContent] = useState(dialogue.content);
  const [parenthetical, setParenthetical] = useState(dialogue.parenthetical ?? '');
  const inputRef = useRef<HTMLInputElement>(null);

  const isEditing = externalEditing !== undefined ? externalEditing : internalEditing;

  // Sync external editing state
  useEffect(() => {
    if (externalEditing) {
      setSpeaker(dialogue.speaker);
      setEmotion(dialogue.emotion ?? 'NEUTRAL');
      setContent(dialogue.content);
      setParenthetical(dialogue.parenthetical ?? '');
    }
  }, [externalEditing, dialogue.speaker, dialogue.emotion, dialogue.content, dialogue.parenthetical]);

  // Focus content input when entering edit mode
  useEffect(() => {
    if (isEditing && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isEditing]);

  const handleSave = () => {
    const trimmedSpeaker = speaker.trim();
    const trimmedContent = content.trim();

    if (!trimmedSpeaker) {
      toast.warning('请填写角色名');
      return;
    }
    if (trimmedSpeaker.length < 2 || trimmedSpeaker.length > 4) {
      toast.warning('角色名需为 2-4 个中文字符');
      return;
    }
    if (!trimmedContent) {
      toast.warning('请填写对白内容');
      return;
    }

    updateDialogueStore(dialogue.id, {
      speaker: trimmedSpeaker,
      emotion: emotion,
      content: trimmedContent,
      parenthetical: parenthetical.trim() || undefined,
    });

    setInternalEditing(false);
    onEditStateChange?.(false);
    toast.success('💾 段落已更新');
  };

  const handleCancel = () => {
    setSpeaker(dialogue.speaker);
    setEmotion(dialogue.emotion ?? 'NEUTRAL');
    setContent(dialogue.content);
    setParenthetical(dialogue.parenthetical ?? '');
    setInternalEditing(false);
    onEditStateChange?.(false);
  };

  return (
    <div
      className={cn(
        'group/dialogue relative text-sm leading-relaxed py-1.5 px-2 -mx-2 rounded-lg transition-colors',
        'hover:bg-white/[0.03]'
      )}
    >
      {isEditing ? (
        /* ── Edit Mode ── */
        <div className="space-y-2">
          <span className="text-xs text-teal-400/60">编辑对白</span>

          {/* Row 1: Character name + Emotion */}
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-1.5 text-xs text-muted-foreground shrink-0">
              <span>角色名</span>
              <input
                className="w-24 bg-white/10 border border-white/10 rounded-md px-2.5 py-1.5 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-teal-500/40"
                value={speaker}
                onChange={(e) => setSpeaker(e.target.value)}
                placeholder="角色名"
                maxLength={4}
              />
            </label>

            <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span>情绪</span>
              <select
                className="bg-white/10 border border-white/10 rounded-md px-2 py-1.5 text-sm text-foreground focus:outline-none focus:border-teal-500/40"
                value={emotion}
                onChange={(e) => setEmotion(e.target.value)}
              >
                {EMOTIONS.map((em) => (
                  <option key={em} value={em} className="bg-zinc-900">
                    {emotionLabel(em)}
                  </option>
                ))}
              </select>
            </label>

            <span className="flex-1" />
          </div>

          {/* Parenthetical */}
          <div>
            <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span>提示</span>
              <input
                className="flex-1 bg-white/10 border border-white/10 rounded-md px-2.5 py-1.5 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-teal-500/40"
                value={parenthetical}
                onChange={(e) => setParenthetical(e.target.value)}
                placeholder="(可选) 如：低声、激动地..."
              />
            </label>
          </div>

          {/* Content */}
          <div>
            <textarea
              ref={inputRef as unknown as React.RefObject<HTMLTextAreaElement>}
              className="w-full bg-white/10 border border-teal-500/30 rounded-lg px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-teal-500/50 resize-y min-h-[50px]"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={2}
              placeholder="输入对白内容..."
              onKeyDown={(e) => {
                if ((e.key === 'Enter' && (e.ctrlKey || e.metaKey))) {
                  e.preventDefault();
                  handleSave();
                }
                if (e.key === 'Escape') handleCancel();
              }}
            />
          </div>

          {/* Action buttons */}
          <div className="flex items-center justify-end gap-2">
            <button
              onClick={handleCancel}
              className="flex items-center gap-1 px-2.5 py-1 text-xs rounded-md hover:bg-white/10 text-muted-foreground transition-colors"
            >
              <X className="h-3 w-3" />
              取消
            </button>
            <button
              onClick={handleSave}
              className="flex items-center gap-1 px-2.5 py-1 text-xs rounded-md bg-teal-500/20 text-teal-300 hover:bg-teal-500/30 transition-colors"
            >
              <Check className="h-3 w-3" />
              保存
            </button>
          </div>
          <p className="text-[10px] text-muted-foreground/40">
            Ctrl+Enter 保存 · Esc 取消
          </p>
        </div>
      ) : (
        /* ── View Mode ── */
        <div className="flex items-baseline gap-x-2 gap-y-0.5 flex-wrap">
          {/* Speaker */}
          <span className="font-semibold text-teal-400 shrink-0 select-none">
            {dialogue.speaker}
          </span>

          {/* Emotion badge */}
          {dialogue.emotion && (
            <span className="text-muted-foreground/70 text-[11px] shrink-0 select-none">
              ({emotionLabel(dialogue.emotion)})
            </span>
          )}

          {/* Parenthetical */}
          {dialogue.parenthetical && (
            <span className="text-muted-foreground/50 text-[11px] shrink-0 italic">
              {dialogue.parenthetical}
            </span>
          )}

          {/* Content */}
          <span className="text-foreground/90 min-w-0">{dialogue.content}</span>

          {/* Reply indicator */}
          {dialogue.replyTo && (
            <span className="text-[10px] text-muted-foreground/40 italic shrink-0">
              ↳ 回复 #{dialogue.replyTo}
            </span>
          )}
        </div>
      )}
    </div>
  );
}
