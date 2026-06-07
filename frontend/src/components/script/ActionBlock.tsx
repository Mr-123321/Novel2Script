'use client';

import { useState, useEffect, useRef } from 'react';
import { useScriptStore } from '@/stores/script-store';
import { cn } from '@/lib/utils';
import { Check, X } from 'lucide-react';
import { toast } from '@/stores/toast-store';
import type { Action } from '@/types/script';

interface ActionBlockProps {
  action: Action;
  /** External edit control — when true, enter edit mode */
  editing?: boolean;
  /** Called when edit mode changes (save/cancel) */
  onEditStateChange?: (editing: boolean) => void;
  /** Register save/cancel callbacks so parent toolbar can trigger them */
  registerActions?: (actions: { save: () => void; cancel: () => void } | null) => void;
  /** Hide internal save/cancel buttons (when parent toolbar provides them) */
  hideActions?: boolean;
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

export function ActionBlock({ action, editing: externalEditing, onEditStateChange, registerActions, hideActions }: ActionBlockProps) {
  const script = useScriptStore((s) => s.script);
  const updateScene = useScriptStore((s) => s.updateScene);

  const [internalEditing, setInternalEditing] = useState(false);
  const [description, setDescription] = useState(action.description);
  const [durationMs, setDurationMs] = useState(action.durationMs?.toString() ?? '');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const isEditing = externalEditing !== undefined ? externalEditing : internalEditing;

  // Sync external editing state
  useEffect(() => {
    if (externalEditing) {
      setDescription(action.description);
      setDurationMs(action.durationMs?.toString() ?? '');
    }
  }, [externalEditing, action.description, action.durationMs]);

  // Focus textarea when entering edit mode
  useEffect(() => {
    if (isEditing && textareaRef.current) {
      textareaRef.current.focus();
      textareaRef.current.select();
    }
  }, [isEditing]);

  const handleSave = () => {
    const trimmed = description.trim();
    if (!trimmed) {
      toast.warning('请填写动作内容');
      return;
    }

    const scene = script?.scenes.find((s) => s.id === action.sceneId);
    if (!scene) return;

    const updatedActions = scene.actions.map((a) =>
      a.id === action.id
        ? {
            ...a,
            description: trimmed,
            durationMs: durationMs ? parseInt(durationMs, 10) || undefined : undefined,
          }
        : a
    );
    updateScene(action.sceneId, {
      actions: updatedActions as unknown as typeof scene.actions,
    } as Partial<typeof scene>);

    setInternalEditing(false);
    onEditStateChange?.(false);
    toast.success('💾 段落已更新');
  };

  const handleCancel = () => {
    setDescription(action.description);
    setDurationMs(action.durationMs?.toString() ?? '');
    setInternalEditing(false);
    onEditStateChange?.(false);
  };

  // Keep latest handleSave/handleCancel in refs to avoid stale closure in registerActions
  const handleSaveRef = useRef(handleSave);
  const handleCancelRef = useRef(handleCancel);
  handleSaveRef.current = handleSave;
  handleCancelRef.current = handleCancel;

  // Register save/cancel actions for parent toolbar (via refs to always call latest)
  useEffect(() => {
    if (isEditing && registerActions) {
      registerActions({
        save: () => handleSaveRef.current(),
        cancel: () => handleCancelRef.current(),
      });
    } else if (!isEditing && registerActions) {
      registerActions(null);
    }
    return () => { registerActions?.(null); };
  }, [isEditing]);

  return (
    <div
      className={cn(
        'group/action relative text-sm py-1.5 px-2 -mx-2 rounded-lg transition-colors',
        'hover:bg-white/[0.02]'
      )}
    >
      {isEditing ? (
        /* ── Edit Mode ── */
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1 text-[10px] font-medium text-muted-foreground/70 bg-white/5 rounded px-1.5 py-0.5 uppercase tracking-wider">
              {actionTypeLabel(action.actionType)}
            </span>
            <span className="text-teal-400/60">编辑模式</span>
          </div>

          <textarea
            ref={textareaRef}
            className="w-full bg-white/10 border border-teal-500/30 rounded-lg px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-teal-500/50 resize-y min-h-[60px]"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            placeholder="输入动作描述..."
            onKeyDown={(e) => {
              if ((e.key === 'Enter' && (e.ctrlKey || e.metaKey))) {
                e.preventDefault();
                handleSave();
              }
              if (e.key === 'Escape') handleCancel();
            }}
          />

          {!hideActions && (
            <>
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <span>时长 (ms)</span>
                  <input
                    className="w-20 bg-white/5 border border-white/10 rounded px-2 py-0.5 text-xs text-foreground focus:outline-none focus:border-teal-500/40"
                    value={durationMs}
                    onChange={(e) => setDurationMs(e.target.value)}
                    placeholder="auto"
                  />
                </label>
                <span className="flex-1" />
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
            </>
          )}
        </div>
      ) : (
        /* ── View Mode ── */
        <div className="flex items-start gap-2">
          {/* Action type tag */}
          <span className="inline-flex items-center text-[10px] font-medium text-muted-foreground/70 bg-white/5 rounded px-1.5 py-0.5 shrink-0 uppercase tracking-wider select-none">
            {actionTypeLabel(action.actionType)}
          </span>

          <span className="text-muted-foreground/70 italic flex-1">
            {action.description}
          </span>

          {/* Duration indicator */}
          {action.durationMs && (
            <span className="text-[10px] text-muted-foreground/40 shrink-0">
              ~{(action.durationMs / 1000).toFixed(1)}s
            </span>
          )}
        </div>
      )}
    </div>
  );
}
