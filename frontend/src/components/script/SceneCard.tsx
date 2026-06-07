'use client';

import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useScriptStore } from '@/stores/script-store';
import { sceneHeader, sanitizeSceneHeading, formatTimeOfDay, cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { DialogueBlock } from './DialogueBlock';
import { ActionBlock } from './ActionBlock';
import {
  MapPin,
  Clock,
  MessageSquare,
  Users,
  Pencil,
  Trash2,
  Plus,
  X,
} from 'lucide-react';
import { toast } from '@/stores/toast-store';
import {
  deleteAction,
  deleteDialogueParagraph,
  addAction,
  addDialogue,
} from '@/lib/api';
import type { Scene, Dialogue, Action } from '@/types/script';
import type { Character } from '@/types/character';

/* ────────────────────────────────────────────
 *  Types
 * ──────────────────────────────────────────── */

type ContentItem =
  | { type: 'action'; item: Action }
  | { type: 'dialogue'; item: Dialogue };

type ParagraphType = 'ACTION' | 'REACTION' | 'BEAT' | 'DIALOGUE';

interface InsertFormData {
  paraType: ParagraphType;
  characterName?: string;
  emotion?: string;
  content: string;
}

const EMOTIONS = ['NEUTRAL', 'CALM', 'ANGRY', 'SAD', 'HAPPY', 'SURPRISED', 'FEARFUL'] as const;
const EMOTION_LABELS: Record<string, string> = {
  NEUTRAL: '中性',
  CALM: '平静',
  ANGRY: '愤怒',
  SAD: '悲伤',
  HAPPY: '喜悦',
  SURPRISED: '惊讶',
  FEARFUL: '冷漠',
};

/* ────────────────────────────────────────────
 *  Helpers
 * ──────────────────────────────────────────── */

function getSortedContent(scene: Scene): ContentItem[] {
  const items: ContentItem[] = [
    ...scene.actions.map((a) => ({ type: 'action' as const, item: a })),
    ...scene.dialogues.map((d) => ({ type: 'dialogue' as const, item: d })),
  ];
  items.sort((a, b) => a.item.sequence - b.item.sequence);
  return items;
}

/** Re-number sequences to 10, 20, 30... to leave gaps for future inserts */
function renumberSequences(items: ContentItem[]) {
  items.forEach((ci, i) => {
    ci.item.sequence = (i + 1) * 10;
  });
}

/* ────────────────────────────────────────────
 *  Insert Form Panel
 * ──────────────────────────────────────────── */

function InsertFormPanel({
  characters,
  onInsert,
  onCancel,
}: {
  characters: Character[];
  onInsert: (data: InsertFormData) => void;
  onCancel: () => void;
}) {
  const [paraType, setParaType] = useState<ParagraphType>('ACTION');
  const [characterName, setCharacterName] = useState('');
  const [emotion, setEmotion] = useState('NEUTRAL');
  const [content, setContent] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const isDialogue = paraType === 'DIALOGUE';

  const handleSubmit = () => {
    const errs: Record<string, string> = {};
    if (isDialogue) {
      const trimmedName = characterName.trim();
      if (!trimmedName) errs.characterName = '请填写角色名';
      else if (trimmedName.length < 2 || trimmedName.length > 4)
        errs.characterName = '角色名需为2-4个字符';
    }
    if (!content.trim()) errs.content = '请填写内容';
    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      toast.warning('请填写必填项');
      return;
    }

    onInsert({
      paraType,
      characterName: isDialogue ? characterName.trim() : undefined,
      emotion: isDialogue ? emotion : undefined,
      content: content.trim(),
    });
  };

  const typeLabel = (t: ParagraphType) => {
    const labels: Record<ParagraphType, string> = {
      ACTION: '动作',
      REACTION: '反应',
      BEAT: '节拍',
      DIALOGUE: '对白',
    };
    return labels[t];
  };

  return (
    <div className="ml-8 my-2 rounded-lg border border-emerald-500/20 bg-emerald-500/[0.04] p-4">
      <div className="space-y-3">
        {/* Type selector */}
        <div>
          <label className="text-xs text-muted-foreground mb-1.5 block">段落类型</label>
          <div className="flex flex-wrap gap-1.5">
            {(['ACTION', 'REACTION', 'BEAT', 'DIALOGUE'] as ParagraphType[]).map(
              (t) => (
                <button
                  key={t}
                  onClick={() => {
                    setParaType(t);
                    setErrors({});
                  }}
                  className={cn(
                    'px-2.5 py-1 rounded-md text-xs font-medium transition-colors',
                    paraType === t
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                      : 'bg-white/5 text-muted-foreground border border-transparent hover:border-white/10'
                  )}
                >
                  {typeLabel(t)}
                </button>
              )
            )}
          </div>
        </div>

        {/* Dialogue-specific fields */}
        {isDialogue && (
          <div className="flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span>角色名</span>
              <input
                className={cn(
                  'w-28 bg-white/10 border rounded-md px-2.5 py-1.5 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-teal-500/40',
                  errors.characterName ? 'border-red-500/50' : 'border-white/10'
                )}
                value={characterName}
                onChange={(e) => {
                  setCharacterName(e.target.value);
                  setErrors((prev) => ({ ...prev, characterName: '' }));
                }}
                placeholder="2-4个字符"
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
                    {EMOTION_LABELS[em]}
                  </option>
                ))}
              </select>
            </label>
            {/* Quick-select character */}
            {characters.length > 0 && (
              <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span>或选角色</span>
                <select
                  className="bg-white/10 border border-white/10 rounded-md px-2 py-1.5 text-sm text-foreground focus:outline-none focus:border-teal-500/40 max-w-[120px]"
                  value=""
                  onChange={(e) => {
                    if (e.target.value) setCharacterName(e.target.value);
                  }}
                >
                  <option value="" className="bg-zinc-900">
                    --
                  </option>
                  {characters.map((c) => (
                    <option
                      key={c.id}
                      value={c.canonicalName}
                      className="bg-zinc-900"
                    >
                      {c.canonicalName}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
        )}

        {/* Content */}
        <div>
          <textarea
            className={cn(
              'w-full bg-white/10 border rounded-lg px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-emerald-500/40 resize-y min-h-[60px]',
              errors.content ? 'border-red-500/50' : 'border-white/10'
            )}
            value={content}
            onChange={(e) => {
              setContent(e.target.value);
              setErrors((prev) => ({ ...prev, content: '' }));
            }}
            placeholder={isDialogue ? '输入对白内容...' : '输入描述内容...'}
            rows={3}
            onKeyDown={(e) => {
              if ((e.key === 'Enter' && (e.ctrlKey || e.metaKey))) {
                e.preventDefault();
                handleSubmit();
              }
            }}
            autoFocus
          />
        </div>

        {/* Buttons */}
        <div className="flex items-center justify-between">
          <span className="text-[10px] text-muted-foreground/40">
            Ctrl+Enter 确认
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={onCancel}
              className="px-3 py-1 text-xs rounded-md hover:bg-white/10 text-muted-foreground transition-colors"
            >
              取消
            </button>
            <button
              onClick={handleSubmit}
              className="flex items-center gap-1 px-3 py-1 text-xs rounded-md bg-emerald-500/20 text-emerald-300 hover:bg-emerald-500/30 transition-colors"
            >
              <Plus className="h-3 w-3" />
              确认插入
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────
 *  SceneCard Component
 * ──────────────────────────────────────────── */

interface SceneCardProps {
  scene: Scene;
  selected?: boolean;
  /** When false (reading mode), all editing controls are hidden */
  editable?: boolean;
}

export function SceneCard({ scene, selected, editable = false }: SceneCardProps) {
  const { selectedSceneId, selectScene, script, setScript } = useScriptStore();
  const isSelected = selected ?? (selectedSceneId === scene.id);

  // Drag state
  const [dragItemIdx, setDragItemIdx] = useState<number | null>(null);
  const [dragOverIdx, setDragOverIdx] = useState<number | null>(null);

  // Paragraph operation state
  const [editingId, setEditingId] = useState<number | null>(null);
  const [insertAfterIdx, setInsertAfterIdx] = useState<number | null>(null);

  // Ref to call save/cancel on the currently editing block
  const blockActionsRef = useRef<{ save: () => void; cancel: () => void } | null>(null);

  // Clear all editing states when switching to reading mode
  useEffect(() => {
    if (!editable) {
      setEditingId(null);
      setInsertAfterIdx(null);
      setDragItemIdx(null);
      setDragOverIdx(null);
    }
  }, [editable]);

  const contentItems = useMemo(() => getSortedContent(scene), [scene]);

  const characterMap = useMemo(
    () => new Map((script?.characters ?? []).map((c) => [c.id, c] as const)),
    [script?.characters]
  );
  const sceneCharacters = useMemo(
    () =>
      (scene.characterIds ?? [])
        .map((id) => characterMap.get(id))
        .filter((c): c is Character => c !== undefined),
    [scene.characterIds, characterMap]
  );

  /* ── Reorder ── */
  const handleReorder = useCallback(
    (fromIdx: number, toIdx: number) => {
      if (fromIdx === toIdx || !script) return;

      const items = [...contentItems];
      const [moved] = items.splice(fromIdx, 1);
      items.splice(toIdx, 0, moved);
      renumberSequences(items);

      // Split back into actions and dialogues
      const newActions: Action[] = [];
      const newDialogues: Dialogue[] = [];
      for (const ci of items) {
        if (ci.type === 'action') newActions.push(ci.item as Action);
        else newDialogues.push(ci.item as Dialogue);
      }

      const updatedScenes = script.scenes.map((s) =>
        s.id === scene.id
          ? { ...s, actions: newActions, dialogues: newDialogues }
          : s
      );
      setScript({ ...script, scenes: updatedScenes });
      toast.success('📦 段落已移动');
    },
    [contentItems, script, scene.id, setScript]
  );

  /* ── Delete ── */
  const handleDelete = useCallback(
    async (item: ContentItem) => {
      if (!script || item.item.id == null) {
        toast.warning('此段落数据异常，无法删除');
        return;
      }

      try {
        if (item.type === 'action') {
          await deleteAction(script.id!, scene.id, item.item.id);
        } else {
          await deleteDialogueParagraph(script.id!, scene.id, item.item.id);
        }
      } catch {
        toast.error('删除失败，请重试');
        return;
      }

      const updatedScenes = script.scenes.map((s) => {
        if (s.id !== scene.id) return s;
        if (item.type === 'action') {
          const filtered = s.actions.filter((a) => a.id !== item.item.id);
          renumberSequences(
            getSortedContent({ ...s, actions: filtered, dialogues: s.dialogues } as Scene)
          );
          return { ...s, actions: filtered };
        } else {
          const filtered = s.dialogues.filter((d) => d.id !== item.item.id);
          renumberSequences(
            getSortedContent({ ...s, actions: s.actions, dialogues: filtered } as Scene)
          );
          return { ...s, dialogues: filtered };
        }
      });

      setScript({ ...script, scenes: updatedScenes });
      setEditingId(null);
      toast.success('段落已删除');
    },
    [script, scene.id, setScript]
  );

  /* ── Insert ── */
  const handleInsert = useCallback(
    async (afterIdx: number, data: InsertFormData) => {
      if (!script) return;

      const afterSequence =
        afterIdx >= 0 ? contentItems[afterIdx]?.item.sequence ?? 0 : 0;
      const beforeSequence =
        afterIdx + 1 < contentItems.length
          ? contentItems[afterIdx + 1]?.item.sequence ?? afterSequence + 20
          : afterSequence + 20;
      const newSeq = Math.round((afterSequence + beforeSequence) / 2);

      // Call backend API first
      let serverId: number;
      try {
        if (data.paraType === 'DIALOGUE') {
          const res = await addDialogue(script.id!, scene.id, {
            speaker: data.characterName ?? '未知',
            content: data.content,
            emotion: data.emotion ?? 'NEUTRAL',
            sequence: newSeq,
          });
          serverId = res.id;
        } else {
          const res = await addAction(script.id!, scene.id, {
            description: data.content,
            actionType: data.paraType,
            sequence: newSeq,
          });
          serverId = res.id;
        }
      } catch {
        toast.error('插入失败，请刷新后重试');
        return;
      }

      // On success, update local state with server-assigned ID
      const updatedScenes = script.scenes.map((s) => {
        if (s.id !== scene.id) return s;

        if (data.paraType === 'DIALOGUE') {
          const newDialogue: Dialogue = {
            id: serverId,
            sceneId: scene.id,
            characterId: 0,
            sequence: newSeq,
            speaker: data.characterName ?? '未知',
            emotion: data.emotion ?? 'NEUTRAL',
            content: data.content,
            parenthetical: undefined,
            replyTo: undefined,
          };
          const newDialogues = [...s.dialogues, newDialogue];
          const merged = getSortedContent({
            ...s,
            actions: s.actions,
            dialogues: newDialogues,
          } as Scene);
          renumberSequences(merged);
          return {
            ...s,
            dialogues: merged
              .filter((ci) => ci.type === 'dialogue')
              .map((ci) => ci.item as Dialogue),
          };
        } else {
          const newAction: Action = {
            id: serverId,
            sceneId: scene.id,
            characterId: undefined,
            sequence: newSeq,
            actionType: data.paraType,
            description: data.content,
            durationMs: undefined,
          };
          const newActions = [...s.actions, newAction];
          const merged = getSortedContent({
            ...s,
            actions: newActions,
            dialogues: s.dialogues,
          } as Scene);
          renumberSequences(merged);
          return {
            ...s,
            actions: merged
              .filter((ci) => ci.type === 'action')
              .map((ci) => ci.item as Action),
          };
        }
      });

      setScript({ ...script, scenes: updatedScenes });
      setInsertAfterIdx(null);
      toast.success('✅ 段落已插入');
    },
    [script, scene.id, contentItems, setScript]
  );

  /* ── Drag handlers ── */
  const handleDragStart = useCallback((idx: number) => {
    setDragItemIdx(idx);
    setDragOverIdx(null);
  }, []);

  const handleDragOver = useCallback(
    (e: React.DragEvent, idx: number) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      if (dragItemIdx !== null && dragItemIdx !== idx) {
        setDragOverIdx(idx);
      }
    },
    [dragItemIdx]
  );

  const handleDragLeave = useCallback(() => {
    // Don't clear on leave — could be entering a child
  }, []);

  const handleDrop = useCallback(
    (idx: number) => {
      if (dragItemIdx !== null && dragItemIdx !== idx) {
        handleReorder(dragItemIdx, idx);
      }
      setDragItemIdx(null);
      setDragOverIdx(null);
    },
    [dragItemIdx, handleReorder]
  );

  const handleDragEnd = useCallback(() => {
    if (dragItemIdx !== null && dragOverIdx === null) {
      // Dropped outside valid target — cancel
      toast.info('移动已取消');
    }
    setDragItemIdx(null);
    setDragOverIdx(null);
  }, [dragItemIdx, dragOverIdx]);

  /* ── Render ── */
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
      {/* ── Scene Header ── */}
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
                {sanitizeSceneHeading(scene.sceneHeading ?? '') || sceneHeader(scene)}
              </span>
            </span>
            {formatTimeOfDay(scene.timeOfDay) && (
            <span className="flex items-center gap-1.5">
              <Clock className="h-3 w-3" /> {formatTimeOfDay(scene.timeOfDay)}
            </span>
            )}
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

      {/* ── Characters in scene ── */}
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

      {/* ── Summary ── */}
      {scene.summary && (
        <p className="text-sm text-muted-foreground mb-4 ml-8 leading-relaxed">
          {scene.summary}
        </p>
      )}

      {/* ── Content Items ── */}
      <div className="space-y-0 ml-8">
        {/* Insert before first paragraph */}
        {editable && contentItems.length > 0 && (
          <InsertBetweenButton
            label="在开头插入段落"
            isActive={insertAfterIdx === -1}
            onToggle={() =>
              setInsertAfterIdx(insertAfterIdx === -1 ? null : -1)
            }
          />
        )}
        {editable && insertAfterIdx === -1 && (
          <InsertFormPanel
            characters={sceneCharacters}
            onInsert={(data) => handleInsert(-1, data)}
            onCancel={() => setInsertAfterIdx(null)}
          />
        )}

        {contentItems.length === 0 && (
          <div className="py-4 text-center">
            <p className="text-xs text-muted-foreground mb-3">此场景暂无内容</p>
            {editable && (
              <>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setInsertAfterIdx(-1);
                  }}
                  className="inline-flex items-center gap-1 px-3 py-1.5 text-xs rounded-md bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 transition-colors"
                >
                  <Plus className="h-3 w-3" />
                  添加第一个段落
                </button>
                {insertAfterIdx === -1 && (
                  <div className="mt-3 text-left">
                    <InsertFormPanel
                      characters={sceneCharacters}
                      onInsert={(data) => handleInsert(-1, data)}
                      onCancel={() => setInsertAfterIdx(null)}
                    />
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {contentItems.map((ci, idx) => (
          <div key={`${ci.type}-${ci.item.id ?? 'idx-' + idx}`} className="relative">
            {/* Drop indicator (edit mode only) */}
            {editable && dragOverIdx === idx && dragItemIdx !== idx && (
              <div className="absolute -top-1 left-0 right-0 z-10 flex items-center gap-2 pointer-events-none">
                <div className="flex-1 border-t-2 border-dashed border-blue-400/60" />
                <span className="text-[10px] text-blue-400/80 font-medium shrink-0">
                  ⬇️ 松手放置在此处
                </span>
                <div className="flex-1 border-t-2 border-dashed border-blue-400/60" />
              </div>
            )}

            {/* Paragraph row */}
            <div
              draggable={false}
              onDragOver={editable ? (e) => handleDragOver(e, idx) : undefined}
              onDragLeave={editable ? handleDragLeave : undefined}
              onDrop={
                editable
                  ? (e) => {
                      e.preventDefault();
                      handleDrop(idx);
                    }
                  : undefined
              }
              onClick={(e) => e.stopPropagation()}
              className={cn(
                'group/row relative rounded-lg transition-all duration-200',
                editable && dragItemIdx === idx && 'opacity-40 shadow-2xl scale-[0.98]',
                editable && dragOverIdx === idx && dragItemIdx !== idx && 'mt-6'
              )}
            >
              {/* Paragraph content */}
              <div
                onDoubleClick={editable ? () => setEditingId(ci.item.id) : undefined}
                className="min-h-[1.5rem]"
              >
                {ci.type === 'action' ? (
                  <ActionBlock
                    action={ci.item as Action}
                    editing={editable ? editingId === ci.item.id : false}
                    onEditStateChange={(isEditing) => {
                      if (!isEditing) setEditingId(null);
                    }}
                    registerActions={(actions) => {
                      blockActionsRef.current = actions;
                    }}
                    hideActions={editable && editingId === ci.item.id}
                  />
                ) : (
                  <DialogueBlock
                    dialogue={ci.item as Dialogue}
                    editing={editable ? editingId === ci.item.id : false}
                    onEditStateChange={(isEditing) => {
                      if (!isEditing) setEditingId(null);
                    }}
                    registerActions={(actions) => {
                      blockActionsRef.current = actions;
                    }}
                    hideActions={editable && editingId === ci.item.id}
                  />
                )}
              </div>

              {/* Hover pencil trigger */}
              {editable && editingId !== ci.item.id && (
                <div className="absolute right-0 top-0 opacity-0 group-hover/row:opacity-100 transition-opacity duration-150 z-10">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setEditingId(ci.item.id);
                    }}
                    className="p-1 rounded-md hover:bg-teal-500/20 text-muted-foreground hover:text-teal-400 transition-colors"
                    title="修改此段落"
                  >
                    <Pencil className="h-3.5 w-3.5" />
                  </button>
                </div>
              )}

              {/* Toolbar: save / cancel / delete */}
              {editable && editingId === ci.item.id && (
                <div className="mt-2 flex items-center gap-0.5 border-t border-white/[0.06] pt-2 animate-in fade-in duration-150">
                  <button
                    onClick={() => blockActionsRef.current?.save()}
                    className="flex items-center gap-1 px-2 py-1 rounded-md text-xs text-muted-foreground hover:text-teal-400 hover:bg-teal-500/10 transition-colors"
                    title="保存修改"
                  >
                    <Pencil className="h-3.5 w-3.5" />
                    保存
                  </button>
                  <button
                    onClick={() => {
                      blockActionsRef.current?.cancel();
                      setEditingId(null);
                    }}
                    className="flex items-center gap-1 px-2 py-1 rounded-md text-xs text-muted-foreground hover:text-foreground hover:bg-white/10 transition-colors"
                    title="取消修改"
                  >
                    <X className="h-3.5 w-3.5" />
                    取消
                  </button>
                  <button
                    onClick={() => handleDelete(ci)}
                    className="flex items-center gap-1 px-2 py-1 rounded-md text-xs text-muted-foreground hover:text-red-400 hover:bg-red-500/10 transition-colors"
                    title="删除段落"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    删除
                  </button>
                </div>
              )}
            </div>

            {/* Insert between button */}
            {editable && (
              <InsertBetweenButton
                label={`在"${
                  ci.type === 'dialogue'
                    ? (ci.item as Dialogue).speaker +
                      '：' +
                      (ci.item as Dialogue).content
                    : (ci.item as Action).description
                }"之后插入段落`.slice(0, 40) + '…之后插入'}
                isActive={insertAfterIdx === idx}
                onToggle={() =>
                  setInsertAfterIdx(insertAfterIdx === idx ? null : idx)
                }
              />
            )}

            {/* Insert form */}
            {editable && insertAfterIdx === idx && (
              <div className="mt-1">
                <InsertFormPanel
                  characters={sceneCharacters}
                  onInsert={(data) => handleInsert(idx, data)}
                  onCancel={() => setInsertAfterIdx(null)}
                />
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Selected indicator line */}
      {isSelected && (
        <div className="absolute left-0 top-4 bottom-4 w-0.5 rounded-full bg-gradient-to-b from-teal-400 to-cyan-500" />
      )}
    </div>
  );
}

/* ────────────────────────────────────────────
 *  InsertBetweenButton (small hover button)
 * ──────────────────────────────────────────── */

function InsertBetweenButton({
  label,
  isActive,
  onToggle,
}: {
  label: string;
  isActive: boolean;
  onToggle: () => void;
}) {
  return (
    <div className="relative h-3 flex items-center justify-center group/insert">
      {/* Thin line visible on row hover */}
      <div className="absolute inset-x-0 top-1/2 -translate-y-1/2 border-t border-transparent group-hover/insert:border-emerald-500/15 transition-colors" />

      <button
        onClick={(e) => {
          e.stopPropagation();
          onToggle();
        }}
        className={cn(
          'relative z-10 flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium transition-all duration-150',
          isActive
            ? 'bg-emerald-500/20 text-emerald-300 opacity-100'
            : 'bg-emerald-500/10 text-emerald-400/60 opacity-0 group-hover/insert:opacity-100 hover:bg-emerald-500/20 hover:text-emerald-300'
        )}
        title={label}
      >
        <Plus className="h-2.5 w-2.5" />
        <span className="hidden group-hover/insert:inline">在此之后插入段落</span>
      </button>
    </div>
  );
}
