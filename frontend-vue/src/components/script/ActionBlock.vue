<template>
  <div class="action-block" :class="{ 'action-block--editing': isEditing }">
    <!-- Edit Mode -->
    <div v-if="isEditing" class="action-edit">
      <div class="edit-header">
        <span class="action-type-tag">{{ actionTypeLabel(action.actionType) }}</span>
        <span class="edit-indicator">编辑模式</span>
      </div>

      <textarea
        ref="textareaRef"
        class="edit-textarea"
        v-model="localDescription"
        rows="3"
        placeholder="输入动作描述..."
        @keydown="handleKeydown"
      ></textarea>

      <!-- Actions (hidden when parent toolbar provides them) -->
      <div v-if="!hideActions" class="edit-footer">
        <label class="duration-field">
          <span>时长 (ms)</span>
          <input
            class="duration-input"
            v-model="localDurationMs"
            placeholder="auto"
          />
        </label>
        <span class="flex-1"></span>
        <button class="btn-cancel" @click="handleCancel">取消</button>
        <button class="btn-save" @click="handleSave">保存</button>
      </div>
      <p v-if="!hideActions" class="shortcut-hint">Ctrl+Enter 保存 · Esc 取消</p>
    </div>

    <!-- View Mode -->
    <div v-else class="action-view">
      <span class="action-type-tag">{{ actionTypeLabel(action.actionType) }}</span>
      <span class="action-desc">{{ action.description }}</span>
      <span v-if="action.durationMs" class="action-duration">
        ~{{ (action.durationMs / 1000).toFixed(1) }}s
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, useTemplateRef, onMounted, getCurrentInstance } from 'vue'
import { useScriptStore } from '@/stores/script'
import { toast } from '@/stores/toast'
import type { Action } from '@/types/script'

const props = defineProps<{
  action: Action
  editing?: boolean
  hideActions?: boolean
}>()

const emit = defineEmits<{
  'edit-state-change': [editing: boolean]
  'register-actions': [actions: { save: () => void; cancel: () => void } | null]
}>()

const store = useScriptStore()
const internalEditing = ref(false)
const localDescription = ref(props.action.description)
const localDurationMs = ref(props.action.durationMs?.toString() ?? '')
const textareaRef = useTemplateRef<HTMLTextAreaElement>('textareaRef')

const isEditing = computed(() =>
  props.editing !== undefined ? props.editing : internalEditing.value
)

// Sync when entering edit mode
watch(isEditing, (val) => {
  if (val) {
    localDescription.value = props.action.description
    localDurationMs.value = props.action.durationMs?.toString() ?? ''
  }
  // Focus textarea
  if (val) {
    setTimeout(() => {
      textareaRef.value?.focus()
      textareaRef.value?.select()
    }, 50)
  }
})

function actionTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    ACTION: '动作',
    REACTION: '反应',
    BEAT: '节拍',
    BUSINESS: '调度',
  }
  return labels[type] ?? type
}

function handleSave() {
  const trimmed = localDescription.value.trim()
  if (!trimmed) {
    toast.warning('请填写动作内容')
    return
  }

  const script = store.script
  const scene = script?.scenes.find((s) => s.id === props.action.sceneId)
  if (!scene) return

  const updatedActions = scene.actions.map((a) =>
    a.id === props.action.id
      ? {
          ...a,
          description: trimmed,
          durationMs: localDurationMs.value ? parseInt(localDurationMs.value, 10) || undefined : undefined,
        }
      : a
  )
  store.updateScene(props.action.sceneId, {
    actions: updatedActions as unknown as typeof scene.actions,
  } as Partial<typeof scene>)

  internalEditing.value = false
  emit('edit-state-change', false)
  toast.success('💾 段落已更新')
}

function handleCancel() {
  localDescription.value = props.action.description
  localDurationMs.value = props.action.durationMs?.toString() ?? ''
  internalEditing.value = false
  emit('edit-state-change', false)
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
.action-block {
  position: relative;
  padding: 6px 8px;
  margin: 0 -8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.action-block:hover {
  background: rgba(255, 255, 255, 0.02);
}

.action-block--editing {
  background: rgba(61, 184, 176, 0.03);
}

/* View Mode */
.action-view {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  line-height: 1.6;
}

.action-type-tag {
  display: inline-flex;
  align-items: center;
  font-size: 10px;
  font-weight: 500;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.05);
  padding: 0px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  flex-shrink: 0;
}

.action-desc {
  color: var(--text-secondary);
  font-style: italic;
  flex: 1;
  min-width: 0;
}

.action-duration {
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0.5;
  flex-shrink: 0;
}

/* Edit Mode */
.action-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.edit-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.edit-indicator {
  font-size: 12px;
  color: var(--teal-primary);
  opacity: 0.6;
}

.edit-textarea {
  width: 100%;
  min-height: 60px;
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
  gap: 12px;
}

.duration-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.duration-input {
  width: 72px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
  outline: none;
}

.duration-input:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.flex-1 {
  flex: 1;
}

.btn-cancel,
.btn-save {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
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
