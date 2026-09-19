/**
 * Derived view over scene-level generation provenance.
 *
 * The backend stores two independent facts:
 *   1. scene.dialogueStatus / scene.actionStatus — COMPLETED | FAILED.
 *      This is a **permanent** record: a manual edit never flips it back,
 *      it only sets the edited item's `source` to MANUAL.
 *   2. dialogue.source / action.source — AI | REGEX | MANUAL.
 *
 * Keeping (1) immutable is what makes "which scenes failed, and who patched
 * them afterwards" traceable, and it prevents the false positive of
 * "added one line → looks like generation succeeded".
 *
 * This module turns those two facts into the answers the UI needs:
 * did generation fail here? has a human filled the gap? which scenes still
 * need attention?
 */
import type { Scene } from '@/types/script';

export type ContentKind = 'dialogue' | 'action';

/** UI copy for each kind — kept here so every surface stays consistent. */
export const KIND_LABELS: Record<ContentKind, string> = {
  dialogue: '对白',
  action: '动作',
};

/** Did generation fail for this kind in this scene? (permanent fact) */
export function hasFailed(scene: Scene, kind: ContentKind): boolean {
  const status = kind === 'dialogue' ? scene.dialogueStatus : scene.actionStatus;
  return status === 'FAILED';
}

/** Number of human-written items of this kind in the scene. */
export function manualCount(scene: Scene, kind: ContentKind): number {
  const items = kind === 'dialogue' ? scene.dialogues : scene.actions;
  return items.filter((i) => i.source === 'MANUAL').length;
}

/** Generation failed for this kind, and a human has since filled the gap. */
export function isPatched(scene: Scene, kind: ContentKind): boolean {
  return hasFailed(scene, kind) && manualCount(scene, kind) > 0;
}

/** Every kind whose generation failed — including already-patched ones. */
export function failedKinds(scene: Scene): ContentKind[] {
  const kinds: ContentKind[] = [];
  if (hasFailed(scene, 'dialogue')) kinds.push('dialogue');
  if (hasFailed(scene, 'action')) kinds.push('action');
  return kinds;
}

export type PendingState = 'none' | 'open' | 'patched';

/**
 * - `none`    — generation succeeded for every kind
 * - `open`    — something failed and is still unpatched (needs attention)
 * - `patched` — something failed but was filled in by hand (the failure
 *               record is deliberately retained)
 */
export function pendingState(scene: Scene): PendingState {
  const kinds = failedKinds(scene);
  if (kinds.length === 0) return 'none';
  return kinds.every((k) => isPatched(scene, k)) ? 'patched' : 'open';
}

/** Scenes whose generation failed and which are not fully patched yet. */
export function openPendingScenes(scenes: Scene[]): Scene[] {
  return scenes.filter((s) => pendingState(s) === 'open');
}
