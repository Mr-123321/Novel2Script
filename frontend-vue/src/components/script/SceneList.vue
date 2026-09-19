<template>
  <div class="scene-list-sidebar">
    <div class="sidebar-scene-header">
      <h3>📜 场景列表</h3>
      <span class="scene-count">{{ scenes.length }} 场</span>
    </div>
    <div class="scene-list">
      <div
        v-for="scene in scenes"
        :key="scene.id"
        class="scene-item"
        :class="{ 'scene-item--active': selectedSceneId === scene.id }"
        :title="sceneTooltip(scene)"
        @click="store.selectScene(scene.id)"
      >
        <span class="scene-num">{{ String(scene.sceneNumber).padStart(2, '0') }}</span>
        <div class="scene-info">
          <span class="scene-title">{{ scene.title || '未命名场景' }}</span>
          <span class="scene-loc">{{ sceneHeaderText(scene) }}</span>
        </div>
        <span class="scene-status-dot" :class="dotClass(scene)"></span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useScriptStore } from '@/stores/script'
import { sanitizeSceneHeading } from '@/lib/utils'
import { failedKinds, pendingState, KIND_LABELS } from '@/lib/generationStatus'
import type { Scene } from '@/types/script'

defineProps<{
  scenes: Scene[]
}>()

const store = useScriptStore()
const selectedSceneId = computed(() => store.selectedSceneId)

function sceneHeaderText(scene: Scene): string {
  if (scene.sceneHeading) {
    const cleaned = sanitizeSceneHeading(scene.sceneHeading)
    if (cleaned !== '未知地点') return cleaned
  }
  let loc = scene.location || '未知地点'
  if (scene.timeOfDay && scene.timeOfDay !== 'UNKNOWN' && scene.timeOfDay !== 'unknown') {
    loc += ` · ${scene.timeOfDay}`
  }
  return loc
}

// ── Generation-status dot ──
// FAILED is a permanent fact: manual edits only flip an item's `source` to
// MANUAL, never the scene-level status. So the dot keeps a gold trace even
// after the scene has been patched up — 'open' means something is still missing.
function dotClass(scene: Scene): string[] {
  const state = pendingState(scene)
  return [
    selectedSceneId.value === scene.id ? 'ss--active' : '',
    state === 'open' ? 'ss--pending' : '',
    state === 'patched' ? 'ss--patched' : '',
  ]
}

function sceneTooltip(scene: Scene): string {
  const state = pendingState(scene)
  if (state === 'none') return sceneHeaderText(scene)

  const what = `${failedKinds(scene).map((k) => KIND_LABELS[k]).join('与')}生成`

  return state === 'open'
    ? `${what}失败，待手动补全`
    : `${what}曾失败，已手动补全（失败记录保留）`
}
</script>

<style scoped>
.scene-list-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px 0;
}

.sidebar-scene-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 12px;
}

.sidebar-scene-header h3 {
  font-family: var(--font-heading);
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 600;
}

.scene-count {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--teal-surface);
  padding: 2px 8px;
  border-radius: 10px;
}

.scene-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.scene-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 2px;
}

.scene-item:hover {
  background: rgba(255, 255, 255, 0.03);
}

.scene-item--active {
  background: var(--teal-surface) !important;
  border: 1px solid rgba(61, 184, 176, 0.15);
  box-shadow: inset 0 1px 0 rgba(61, 184, 176, 0.06);
}

.scene-item--active::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 1px;
  background: linear-gradient(to bottom, var(--teal-primary), #06b6d5);
  box-shadow: 0 0 8px var(--teal-glow);
}

.scene-num {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--teal-primary);
  min-width: 22px;
}

.scene-info {
  flex: 1;
  min-width: 0;
}

.scene-title {
  display: block;
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scene-loc {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scene-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
  box-sizing: border-box;
  transition: all 0.2s;
}

.ss--active {
  background: var(--teal-primary);
  box-shadow: 0 0 6px var(--teal-glow);
}

/* Generation failed here and nothing has been filled in yet — needs attention.
   Declared after .ss--active so it wins when a scene is both selected and open. */
.ss--pending {
  background: var(--warm-gold);
  box-shadow: 0 0 6px var(--warm-gold-glow);
}

/* Generation failed here but the gap was closed by hand — keep a faint trace
   so "which scenes were broken" stays traceable at a glance */
.ss--patched {
  background: transparent;
  border: 1.5px solid var(--warm-gold-muted);
}

.btn-add-scene {
  margin: 12px 16px 0;
  padding: 8px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 12px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px dashed rgba(255, 255, 255, 0.06);
  cursor: not-allowed;
  transition: all 0.2s;
}
</style>
