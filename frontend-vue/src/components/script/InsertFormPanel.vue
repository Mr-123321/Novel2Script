<template>
  <div class="insert-form">
    <div class="form-content">
      <!-- Type selector -->
      <div class="form-group">
        <label class="form-label">段落类型</label>
        <div class="type-buttons">
          <button
            v-for="t in paraTypes"
            :key="t.value"
            class="type-btn"
            :class="{ 'type-btn--active': form.paraType === t.value }"
            @click="form.paraType = t.value; clearErrors()"
          >{{ t.label }}</button>
        </div>
      </div>

      <!-- Dialogue-specific fields -->
      <div v-if="isDialogue" class="form-row">
        <label class="form-label">
          <span>角色名</span>
          <input
            class="form-input name-input"
            :class="{ 'input-error': errors.characterName }"
            v-model="form.characterName"
            placeholder="2-4个字符"
            maxlength="4"
            @input="clearError('characterName')"
          />
        </label>
        <label class="form-label">
          <span>情绪</span>
          <select class="form-select" v-model="form.emotion">
            <option v-for="em in EMOTIONS" :key="em" :value="em">{{ emotionLabels[em] }}</option>
          </select>
        </label>
        <label v-if="characters.length > 0" class="form-label">
          <span>或选角色</span>
          <select class="form-select char-select" v-model="quickChar" @change="onQuickChar">
            <option value="">--</option>
            <option v-for="c in characters" :key="c.id" :value="c.canonicalName">{{ c.canonicalName }}</option>
          </select>
        </label>
      </div>

      <!-- Content -->
      <div class="form-group">
        <textarea
          class="form-textarea"
          :class="{ 'input-error': errors.content }"
          v-model="form.content"
          :placeholder="isDialogue ? '输入对白内容...' : '输入描述内容...'"
          rows="3"
          @keydown="handleKeydown"
          @input="clearError('content')"
          autofocus
        ></textarea>
      </div>

      <!-- Buttons -->
      <div class="form-footer">
        <span class="shortcut-hint">Ctrl+Enter 确认</span>
        <div class="form-actions">
          <button class="btn-cancel" @click="$emit('cancel')">取消</button>
          <button class="btn-confirm" @click="handleSubmit">+ 确认插入</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { toast } from '@/stores/toast'
import type { Character } from '@/types/character'

const EMOTIONS = ['NEUTRAL', 'CALM', 'ANGRY', 'SAD', 'HAPPY', 'SURPRISED', 'FEARFUL'] as const

const emotionLabels: Record<string, string> = {
  NEUTRAL: '中性', CALM: '平静', ANGRY: '愤怒', SAD: '悲伤',
  HAPPY: '喜悦', SURPRISED: '惊讶', FEARFUL: '冷漠',
}

const paraTypes = [
  { value: 'ACTION' as const, label: '动作' },
  { value: 'REACTION' as const, label: '反应' },
  { value: 'BEAT' as const, label: '节拍' },
  { value: 'DIALOGUE' as const, label: '对白' },
]

const props = defineProps<{
  characters: Character[]
}>()

const emit = defineEmits<{
  insert: [data: {
    paraType: string
    characterName?: string
    emotion?: string
    content: string
  }]
  cancel: []
}>()

const form = reactive({
  paraType: 'ACTION' as string,
  characterName: '',
  emotion: 'NEUTRAL',
  content: '',
})

const errors = reactive<Record<string, string>>({})
const quickChar = ref('')

const isDialogue = computed(() => form.paraType === 'DIALOGUE')

function clearError(field: string) {
  delete errors[field]
}

function clearErrors() {
  Object.keys(errors).forEach((k) => delete errors[k])
}

function onQuickChar() {
  if (quickChar.value) {
    form.characterName = quickChar.value
    quickChar.value = ''
  }
}

function handleSubmit() {
  const errs: Record<string, string> = {}
  if (isDialogue.value) {
    const trimmedName = (form.characterName || '').trim()
    if (!trimmedName) errs.characterName = '请填写角色名'
    else if (trimmedName.length < 2 || trimmedName.length > 4) errs.characterName = '角色名需为2-4个字符'
  }
  if (!form.content.trim()) errs.content = '请填写内容'
  if (Object.keys(errs).length > 0) {
    Object.assign(errors, errs)
    toast.warning('请填写必填项')
    return
  }

  emit('insert', {
    paraType: form.paraType,
    characterName: isDialogue.value ? (form.characterName || '').trim() : undefined,
    emotion: isDialogue.value ? form.emotion : undefined,
    content: form.content.trim(),
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    handleSubmit()
  }
}
</script>

<style scoped>
.insert-form {
  margin: 8px 0 8px 24px;
  border-radius: 10px;
  border: 1px solid rgba(61, 184, 176, 0.15);
  background: rgba(61, 184, 176, 0.04);
  padding: 12px;
}

.form-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.form-label span {
  flex-shrink: 0;
}

.type-buttons {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.type-btn {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.type-btn:hover {
  border-color: rgba(255, 255, 255, 0.1);
}

.type-btn--active {
  background: var(--teal-surface);
  color: var(--teal-primary);
  border-color: rgba(61, 184, 176, 0.2);
}

.form-input,
.form-select {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 13px;
  color: var(--text-primary);
  outline: none;
}

.form-input:focus,
.form-select:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.form-select option {
  background: var(--ink-deep-elevated);
  color: var(--text-primary);
}

.name-input { width: 100px; }
.char-select { max-width: 120px; }

.input-error {
  border-color: rgba(194, 59, 34, 0.4) !important;
}

.form-textarea {
  width: 100%;
  min-height: 60px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-primary);
  resize: vertical;
  outline: none;
}

.form-textarea:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.shortcut-hint {
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0.4;
}

.form-actions {
  display: flex;
  gap: 8px;
}

.btn-cancel,
.btn-confirm {
  padding: 4px 12px;
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

.btn-confirm {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--teal-primary);
  background: var(--teal-surface);
}

.btn-confirm:hover {
  background: var(--teal-surface-hover);
}
</style>
