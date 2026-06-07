<template>
  <div class="dialogue-block" :class="{ 'dialogue-block--editing': isEditing }">
    <!-- Edit Mode -->
    <div v-if="isEditing" class="dialogue-edit">
      <span class="edit-label">编辑对白</span>

      <div class="edit-row">
        <label class="field-label">
          <span>角色名</span>
          <input
            ref="inputRef"
            class="field-input name-input"
            v-model="localSpeaker"
            placeholder="角色名"
            maxlength="4"
          />
        </label>
        <label class="field-label">
          <span>情绪</span>
          <select class="field-select" v-model="localEmotion">
            <option v-for="em in EMOTIONS" :key="em" :value="em">
              {{ emotionLabel(em) }}
            </option>
          </select>
        </label>
      </div>

      <div class="edit-row">
        <label class="field-label flex-1">
          <span>提示</span>
          <input
            class="field-input"
            v-model="localParenthetical"
            placeholder="(可选) 如：低声、激动地..."
          />
        </label>
      </div>

      <textarea
        ref="textareaRef"
        class="edit-textarea"
        v-model="localContent"
        rows="2"
        placeholder="输入对白内容..."
        @keydown="handleKeydown"
      ></textarea>

      <!-- Actions -->
      <div class="edit-footer">
        <button class="btn-delete" @click="handleDelete">🗑 删除</button>
        <span class="flex-1"></span>
        <button class="btn-cancel" @click="handleCancel">取消</button>
        <button class="btn-save" @click="handleSave">保存</button>
      </div>
    </div>

    <!-- View Mode -->
    <div v-else class="dialogue-view">
      <span class="speaker">{{ dialogue.speaker }}</span>
      <span v-if="dialogue.emotion" class="emotion">({{ emotionLabel(dialogue.emotion) }})</span>
      <span v-if="dialogue.parenthetical" class="parenthetical">{{ dialogue.parenthetical }}</span>
      <span class="content">{{ dialogue.content }}</span>
      <span v-if="dialogue.replyTo" class="reply-indicator">↳ 回复 #{{ dialogue.replyTo }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, useTemplateRef, onMounted } from 'vue'
import { useScriptStore } from '@/stores/script'
import { emotionLabel } from '@/lib/utils'
import { toast } from '@/stores/toast'
import { deleteDialogueParagraph } from '@/lib/api'
import type { Dialogue } from '@/types/script'

const EMOTIONS = ['NEUTRAL', 'CALM', 'ANGRY', 'SAD', 'HAPPY', 'SURPRISED', 'FEARFUL'] as const

const props = defineProps<{
  dialogue: Dialogue
  editing?: boolean
  hideActions?: boolean
}>()

const emit = defineEmits<{
  'edit-state-change': [editing: boolean]
  'register-actions': [actions: { save: () => void; cancel: () => void } | null]
  'delete': [dialogueId: number]
}>()

const store = useScriptStore()
const internalEditing = ref(false)
const localSpeaker = ref(props.dialogue.speaker)
const localEmotion = ref(props.dialogue.emotion ?? 'NEUTRAL')
const localContent = ref(props.dialogue.content)
const localParenthetical = ref(props.dialogue.parenthetical ?? '')
const inputRef = useTemplateRef<HTMLInputElement>('inputRef')
const textareaRef = useTemplateRef<HTMLTextAreaElement>('textareaRef')

const isEditing = computed(() =>
  props.editing !== undefined ? props.editing : internalEditing.value
)

// Sync when entering edit mode + focus
watch(isEditing, (val) => {
  if (val) {
    localSpeaker.value = props.dialogue.speaker
    localEmotion.value = props.dialogue.emotion ?? 'NEUTRAL'
    localContent.value = props.dialogue.content
    localParenthetical.value = props.dialogue.parenthetical ?? ''
  }
  // Focus name input
  if (val) {
    setTimeout(() => {
      inputRef.value?.focus({ preventScroll: true })
    }, 50)
  }
})

function handleSave() {
  const trimmedSpeaker = localSpeaker.value.trim()
  const trimmedContent = localContent.value.trim()

  if (!trimmedSpeaker) {
    toast.warning('请填写角色名')
    return
  }
  if (trimmedSpeaker.length < 2 || trimmedSpeaker.length > 4) {
    toast.warning('角色名需为 2-4 个中文字符')
    return
  }
  if (!trimmedContent) {
    toast.warning('请填写对白内容')
    return
  }

  store.updateDialogue(props.dialogue.sceneId, props.dialogue.sequence, {
    speaker: trimmedSpeaker,
    emotion: localEmotion.value,
    content: trimmedContent,
    parenthetical: localParenthetical.value.trim() || undefined,
  })

  internalEditing.value = false
  emit('edit-state-change', false)
  toast.success('💾 段落已更新')
}

function handleCancel() {
  localSpeaker.value = props.dialogue.speaker
  localEmotion.value = props.dialogue.emotion ?? 'NEUTRAL'
  localContent.value = props.dialogue.content
  localParenthetical.value = props.dialogue.parenthetical ?? ''
  internalEditing.value = false
  emit('edit-state-change', false)
}

async function handleDelete() {
  if (!store.script?.id) {
    toast.warning('无法获取剧本信息，请刷新后重试')
    return
  }
  try {
    await deleteDialogueParagraph(store.script.id, props.dialogue.sceneId, props.dialogue.id!)
    emit('delete', props.dialogue.id!)
    internalEditing.value = false
    emit('edit-state-change', false)
    toast.success('段落已删除')
  } catch {
    toast.error('删除失败，请重试')
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    handleSave()
  }
  if (e.key === 'Escape') handleCancel()
}

// Register save/cancel for parent toolbar
onMounted(() => {
  if (isEditing.value) {
    emit('register-actions', {
      save: handleSave,
      cancel: handleCancel,
    })
  }
})

watch(() => isEditing.value, (val) => {
  if (val) {
    emit('register-actions', {
      save: handleSave,
      cancel: handleCancel,
    })
  } else {
    emit('register-actions', null)
  }
}, { flush: 'sync' })
</script>

<style scoped>
.dialogue-block {
  position: relative;
  padding: 6px 8px;
  margin: 0 -8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.dialogue-block:hover {
  background: rgba(255, 255, 255, 0.03);
}

.dialogue-block--editing {
  background: rgba(61, 184, 176, 0.03);
}

/* View Mode */
.dialogue-view {
  display: flex;
  align-items: baseline;
  gap: 4px;
  flex-wrap: wrap;
  font-size: 14px;
  line-height: 1.6;
}

.speaker {
  font-weight: 600;
  color: var(--teal-primary);
  flex-shrink: 0;
}

.emotion,
.parenthetical {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
  opacity: 0.7;
}

.parenthetical {
  font-style: italic;
}

.content {
  color: var(--text-primary);
  opacity: 0.9;
  min-width: 0;
}

.reply-indicator {
  font-size: 10px;
  color: var(--text-muted);
  font-style: italic;
  opacity: 0.4;
  flex-shrink: 0;
}

/* Edit Mode */
.dialogue-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.edit-label {
  font-size: 12px;
  color: var(--teal-primary);
  opacity: 0.6;
}

.edit-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.field-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.field-label span {
  flex-shrink: 0;
}

.field-input {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.2s;
}

.field-input:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.name-input {
  width: 96px;
}

.field-select {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
}

.field-select:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.field-select option {
  background: var(--ink-deep-elevated);
  color: var(--text-primary);
}

.flex-1 {
  flex: 1;
}

.edit-textarea {
  width: 100%;
  min-height: 50px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(61, 184, 176, 0.2);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  color: var(--text-primary);
  resize: vertical;
  outline: none;
  transition: border-color 0.2s;
}

.edit-textarea:focus {
  border-color: rgba(61, 184, 176, 0.4);
}

.edit-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
}

.btn-cancel,
.btn-save,
.btn-delete {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-delete {
  color: var(--cinnabar);
}

.btn-delete:hover {
  background: var(--cinnabar-surface);
}

.btn-cancel {
  color: var(--text-muted);
}

.btn-cancel:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.06);
}

.btn-save {
  color: var(--teal-primary);
  background: var(--teal-surface);
}

.btn-save:hover {
  background: var(--teal-surface-hover);
}

.shortcut-hint {
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0.4;
}
</style>
