<template>
  <div
    class="scene-card"
    :class="{ 'scene-card--selected': isSelected }"
    @click="store.selectScene(scene.id)"
  >
    <!-- Selected indicator line -->
    <div v-if="isSelected" class="selected-line"></div>

    <!-- ── Scene Header ── -->
    <div class="scene-header">
      <div class="header-left">
        <h3 class="header-title">
          <span class="scene-number">{{ scene.sceneNumber }}</span>
          <span v-if="scene.title" class="scene-title-text">{{ scene.title }}</span>
        </h3>
        <div class="header-meta">
          <span class="meta-item">📍 {{ displayLocation }}</span>
          <span v-if="displayTime" class="meta-item">🕐 {{ displayTime }}</span>
          <span class="meta-item">💬 {{ scene.dialogues.length }}</span>
        </div>
      </div>
      <div class="header-right">
        <span v-if="scene.mood" class="mood-badge">{{ scene.mood }}</span>
      </div>
    </div>

    <!-- Characters in scene -->
    <div v-if="sceneCharacters.length > 0" class="scene-chars">
      <span v-for="c in sceneCharacters" :key="c.id" class="char-chip">
        {{ c.canonicalName }}
      </span>
    </div>

    <!-- Summary -->
    <p v-if="scene.summary" class="scene-summary">{{ scene.summary }}</p>

    <!-- ── Content Items ── -->
    <div class="content-area">
      <!-- Empty state -->
      <div v-if="contentItems.length === 0" class="empty-state">
        <p>此场景暂无内容</p>
        <button
          v-if="editable"
          class="btn-add-first"
          @click.stop="insertAfterIdx = -1"
        >+ 添加第一个段落</button>
        <InsertFormPanel
          v-if="editable && insertAfterIdx === -1"
          :characters="sceneCharacters"
          @insert="(data) => handleInsert(-1, data)"
          @cancel="insertAfterIdx = null"
        />
      </div>

      <!-- Insert before first (when there ARE items) -->
      <InsertBetweenButton
        v-if="editable && contentItems.length > 0"
        label="在开头插入段落"
        :is-active="insertAfterIdx === -1"
        @toggle="insertAfterIdx = insertAfterIdx === -1 ? null : -1"
      />
      <InsertFormPanel
        v-if="editable && insertAfterIdx === -1"
        :characters="sceneCharacters"
        @insert="(data) => handleInsert(-1, data)"
        @cancel="insertAfterIdx = null"
      />

      <!-- Paragraph rows -->
      <div
        v-for="(ci, idx) in contentItems"
        :key="`${ci.type}-${ci.item.id ?? 'idx-' + idx}`"
        class="paragraph-row"
      >
        <div class="paragraph-content" :class="{ 'paragraph-content--editing': editingId === ci.item.id }">
          <!-- Action Block -->
          <ActionBlock
            v-if="ci.type === 'action'"
            :action="(ci.item as Action)"
            :editing="editable && editingId === ci.item.id"
            :hide-actions="editable && editingId === ci.item.id"
            @edit-state-change="(editing) => { if (!editing) editingId = null }"
            @register-actions="(actions) => handleRegisterActions(ci.item.id, actions)"
          />
          <!-- Dialogue Block -->
          <DialogueBlock
            v-else
            :dialogue="(ci.item as Dialogue)"
            :editing="editable && editingId === ci.item.id"
            :hide-actions="editable && editingId === ci.item.id"
            @edit-state-change="(editing) => { if (!editing) editingId = null }"
            @register-actions="(actions) => handleRegisterActions(ci.item.id, actions)"
            @delete="(id) => handleDeleteParagraph('dialogue', id)"
          />

          <!-- Pencil trigger (left side, hover) -->
          <div
            v-if="editable && editingId !== ci.item.id"
            class="pencil-trigger"
          >
            <button
              class="pencil-btn"
              @click.stop="editingId = ci.item.id ?? -idx - 1"
              title="修改此段落"
            >✏️</button>
          </div>

          <!-- Toolbar: save / cancel / delete -->
          <div
            v-if="editable && editingId !== null && editingId === ci.item.id"
            class="edit-toolbar"
          >
            <button class="toolbar-btn toolbar-btn--save" @click="blockActions.get(ci.item.id)?.save()">
              ✏️ 保存
            </button>
            <button class="toolbar-btn toolbar-btn--cancel" @click="blockActions.get(ci.item.id)?.cancel(); editingId = null">
              ✕ 取消
            </button>
            <button class="toolbar-btn toolbar-btn--delete" @click="handleDelete(ci)">
              🗑 删除
            </button>
          </div>
        </div>

        <!-- Insert between button -->
        <InsertBetweenButton
          v-if="editable"
          label="在此之后插入段落"
          :is-active="insertAfterIdx === idx"
          @toggle="insertAfterIdx = insertAfterIdx === idx ? null : idx"
        />
        <InsertFormPanel
          v-if="editable && insertAfterIdx === idx"
          :characters="sceneCharacters"
          @insert="(data) => handleInsert(idx, data)"
          @cancel="insertAfterIdx = null"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, reactive } from 'vue'
import { useScriptStore } from '@/stores/script'
import { sanitizeSceneHeading, formatTimeOfDay } from '@/lib/utils'
import { toast } from '@/stores/toast'
import { deleteAction, deleteDialogueParagraph, addAction, addDialogue } from '@/lib/api'
import DialogueBlock from './DialogueBlock.vue'
import ActionBlock from './ActionBlock.vue'
import InsertFormPanel from './InsertFormPanel.vue'
import InsertBetweenButton from './InsertBetweenButton.vue'
import type { Scene, Dialogue, Action } from '@/types/script'
import type { Character } from '@/types/character'

type ContentItem =
  | { type: 'action'; item: Action }
  | { type: 'dialogue'; item: Dialogue }

const props = defineProps<{
  scene: Scene
  selected?: boolean
  editable?: boolean
}>()

const store = useScriptStore()
const isSelected = computed(() => props.selected ?? (store.selectedSceneId === props.scene.id))

// State
const editingId = ref<number | null>(null)
const insertAfterIdx = ref<number | null>(null)
const blockActions = reactive<Map<number, { save: () => void; cancel: () => void }>>(new Map())

// Clear editing states when switching to reading mode
watch(() => props.editable, (val) => {
  if (!val) {
    editingId.value = null
    insertAfterIdx.value = null
  }
})

// Derived data
const contentItems = computed<ContentItem[]>(() => {
  const items: ContentItem[] = [
    ...props.scene.actions.map((a) => ({ type: 'action' as const, item: a })),
    ...props.scene.dialogues.map((d) => ({ type: 'dialogue' as const, item: d })),
  ]
  items.sort((a, b) => a.item.sequence - b.item.sequence)
  return items
})

const characterMap = computed(() =>
  new Map((store.script?.characters ?? []).map((c) => [c.id, c] as const))
)

const sceneCharacters = computed(() =>
  (props.scene.characterIds ?? [])
    .map((id) => characterMap.value.get(id))
    .filter((c): c is Character => c !== undefined)
)

const displayLocation = computed(() => {
  if (props.scene.sceneHeading) {
    const cleaned = sanitizeSceneHeading(props.scene.sceneHeading)
    if (cleaned !== '未知地点') return cleaned
  }
  return props.scene.location || '未知地点'
})

const displayTime = computed(() => formatTimeOfDay(props.scene.timeOfDay))

// ── Reorder sequences ──
function renumberSequences(items: ContentItem[]) {
  items.forEach((ci, i) => {
    ci.item.sequence = (i + 1) * 10
  })
}

function getSortedContent(actions: Action[], dialogues: Dialogue[]): ContentItem[] {
  const items: ContentItem[] = [
    ...actions.map((a) => ({ type: 'action' as const, item: a })),
    ...dialogues.map((d) => ({ type: 'dialogue' as const, item: d })),
  ]
  items.sort((a, b) => a.item.sequence - b.item.sequence)
  return items
}

// ── Register actions ──
function handleRegisterActions(id: number, actions: { save: () => void; cancel: () => void } | null) {
  if (actions) {
    blockActions.set(id, actions)
  } else {
    blockActions.delete(id)
  }
}

// ── Delete ──
async function handleDelete(ci: ContentItem) {
  if (!window.confirm('确定删除此段落吗？')) return

  if (!store.script || ci.item.id == null) {
    toast.warning('此段落数据异常，无法删除')
    return
  }

  try {
    if (ci.type === 'action') {
      await deleteAction(store.script.id!, props.scene.id, ci.item.id)
    } else {
      await deleteDialogueParagraph(store.script.id!, props.scene.id, ci.item.id)
    }
  } catch {
    toast.error('删除失败，请重试')
    return
  }

  const updatedScenes = store.script.scenes.map((s) => {
    if (s.id !== props.scene.id) return s
    if (ci.type === 'action') {
      const filtered = s.actions.filter((a) => a.id !== ci.item.id)
      const merged = getSortedContent(filtered, s.dialogues)
      renumberSequences(merged)
      return { ...s, actions: filtered }
    } else {
      const filtered = s.dialogues.filter((d) => d.id !== ci.item.id)
      const merged = getSortedContent(s.actions, filtered)
      renumberSequences(merged)
      return { ...s, dialogues: filtered }
    }
  })

  store.setScript({ ...store.script, scenes: updatedScenes })
  editingId.value = null
  toast.success('段落已删除')
}

// Handle delete emitted from DialogueBlock (API already called)
function handleDeleteParagraph(type: 'dialogue', id: number) {
  if (!store.script) return

  const updatedScenes = store.script.scenes.map((s) => {
    if (s.id !== props.scene.id) return s
    if (type === 'dialogue') {
      const filtered = s.dialogues.filter((d) => d.id !== id)
      return { ...s, dialogues: filtered }
    }
    return s
  })

  store.setScript({ ...store.script, scenes: updatedScenes })
  editingId.value = null
}

// ── Insert ──
async function handleInsert(afterIdx: number, data: {
  paraType: string
  characterName?: string
  emotion?: string
  content: string
}) {
  if (!store.script) return

  const items = contentItems.value
  const afterSequence = afterIdx >= 0 ? items[afterIdx]?.item.sequence ?? 0 : 0
  const beforeSequence = afterIdx + 1 < items.length
    ? items[afterIdx + 1]?.item.sequence ?? afterSequence + 20
    : afterSequence + 20
  const newSeq = Math.round((afterSequence + beforeSequence) / 2)

  let serverId: number
  try {
    if (data.paraType === 'DIALOGUE') {
      const res = await addDialogue(store.script.id!, props.scene.id, {
        speaker: data.characterName ?? '未知',
        content: data.content,
        emotion: data.emotion ?? 'NEUTRAL',
        sequence: newSeq,
      })
      serverId = res.id
    } else {
      const res = await addAction(store.script.id!, props.scene.id, {
        description: data.content,
        actionType: data.paraType,
        sequence: newSeq,
      })
      serverId = res.id
    }
  } catch {
    toast.error('插入失败，请刷新后重试')
    return
  }

  const updatedScenes = store.script.scenes.map((s) => {
    if (s.id !== props.scene.id) return s

    if (data.paraType === 'DIALOGUE') {
      const newDialogue: Dialogue = {
        id: serverId,
        sceneId: props.scene.id,
        characterId: 0,
        sequence: newSeq,
        speaker: data.characterName ?? '未知',
        emotion: data.emotion ?? 'NEUTRAL',
        content: data.content,
        parenthetical: undefined,
        replyTo: undefined,
      }
      const merged = getSortedContent(s.actions, [...s.dialogues, newDialogue])
      renumberSequences(merged)
      return {
        ...s,
        dialogues: merged.filter((ci) => ci.type === 'dialogue').map((ci) => ci.item as Dialogue),
      }
    } else {
      const newAction: Action = {
        id: serverId,
        sceneId: props.scene.id,
        characterId: undefined,
        sequence: newSeq,
        actionType: data.paraType,
        description: data.content,
        durationMs: undefined,
      }
      const merged = getSortedContent([...s.actions, newAction], s.dialogues)
      renumberSequences(merged)
      return {
        ...s,
        actions: merged.filter((ci) => ci.type === 'action').map((ci) => ci.item as Action),
      }
    }
  })

  store.setScript({ ...store.script, scenes: updatedScenes })
  insertAfterIdx.value = null
  toast.success('✅ 段落已插入')
}
</script>

<style scoped>
.scene-card {
  position: relative;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.02);
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s;
}

.scene-card:hover {
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
}

.scene-card--selected {
  border-color: rgba(61, 184, 176, 0.3);
  background: rgba(61, 184, 176, 0.04);
  box-shadow: 0 0 30px rgba(45, 212, 191, 0.06);
}

.selected-line {
  position: absolute;
  left: 0;
  top: 16px;
  bottom: 16px;
  width: 2px;
  border-radius: 1px;
  background: linear-gradient(to bottom, var(--teal-primary), #06b6d5);
}

/* Header */
.scene-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.header-left {
  min-width: 0;
}

.header-title {
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.scene-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  width: 24px;
  border-radius: 6px;
  background: var(--teal-surface);
  color: var(--teal-primary);
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.scene-title-text {
  color: var(--text-secondary);
  font-weight: 400;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 8px;
  margin-left: 32px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.mood-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-secondary);
}

/* Characters */
.scene-chars {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  margin-left: 32px;
  flex-wrap: wrap;
}

.char-chip {
  display: inline-flex;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--teal-surface);
  color: var(--teal-primary);
  font-weight: 500;
}

/* Summary */
.scene-summary {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 16px;
  margin-left: 32px;
  line-height: 1.6;
}

/* Content Area */
.content-area {
  margin-left: 32px;
}

.empty-state {
  padding: 24px;
  text-align: center;
}

.empty-state p {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.btn-add-first {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--teal-primary);
  background: var(--teal-surface);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-add-first:hover {
  background: var(--teal-surface-hover);
}

/* Paragraph rows */
.paragraph-row {
  position: relative;
}

.paragraph-content {
  position: relative;
  border-radius: 8px;
  transition: all 0.2s;
}

.paragraph-content--editing {
  background: rgba(61, 184, 176, 0.02);
}

/* Pencil trigger */
.pencil-trigger {
  position: absolute;
  left: -28px;
  top: 4px;
  opacity: 0;
  transition: opacity 0.15s;
  z-index: 10;
}

.paragraph-content:hover .pencil-trigger {
  opacity: 1;
}

.pencil-btn {
  padding: 4px;
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.pencil-btn:hover {
  color: var(--teal-primary);
  background: var(--teal-surface);
}

/* Edit toolbar */
.edit-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-muted);
}

.toolbar-btn--save:hover {
  color: var(--teal-primary);
  background: var(--teal-surface);
}

.toolbar-btn--cancel:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.06);
}

.toolbar-btn--delete:hover {
  color: var(--cinnabar);
  background: var(--cinnabar-surface);
}
</style>
