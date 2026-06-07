<template>
  <Teleport to="body">
    <div class="editor-overlay" @click.self="$emit('close')">
      <div class="editor-drawer">
        <!-- Header -->
        <div class="drawer-header">
          <h3 class="drawer-title">
            <span class="title-icon">👤</span>
            编辑角色 — {{ character.canonicalName }}
          </h3>
          <button class="drawer-close" @click="$emit('close')">✕</button>
        </div>

        <!-- Body -->
        <div class="drawer-body">
          <!-- Name -->
          <div class="field-group">
            <label class="field-label">标准名称</label>
            <input
              class="field-input"
              :value="character.canonicalName"
              @input="updateField('canonicalName', ($event.target as HTMLInputElement).value)"
            />
          </div>

          <!-- Role Type & Gender -->
          <div class="field-row">
            <div class="field-group flex-1">
              <label class="field-label">角色类型</label>
              <div class="chip-select">
                <button
                  v-for="rt in roleTypes"
                  :key="rt.value"
                  class="chip-option"
                  :class="{ 'chip-option--active': character.roleType === rt.value }"
                  @click="updateField('roleType', rt.value)"
                >{{ roleLabel(rt.value) }}</button>
              </div>
            </div>
            <div class="field-group">
              <label class="field-label">性别</label>
              <div class="chip-select">
                <button
                  v-for="g in genders"
                  :key="g.value"
                  class="chip-option"
                  :class="{ 'chip-option--active': character.gender === g.value }"
                  @click="updateField('gender', g.value)"
                >{{ g.label }}</button>
              </div>
            </div>
          </div>

          <!-- Description -->
          <div class="field-group">
            <label class="field-label">角色描述</label>
            <textarea
              class="field-textarea"
              :value="character.description"
              @input="updateField('description', ($event.target as HTMLTextAreaElement).value)"
              rows="3"
              placeholder="描述角色的背景和性格..."
            ></textarea>
          </div>

          <!-- Aliases -->
          <div class="field-group">
            <label class="field-label">别名</label>
            <div class="chip-list">
              <span v-for="(alias, i) in character.aliases" :key="i" class="chip-removable">
                {{ alias }}
                <button class="chip-remove" @click="removeAlias(i)">✕</button>
              </span>
              <input
                class="chip-input"
                placeholder="添加别名..."
                @keydown.enter="addAlias(($event.target as HTMLInputElement))"
              />
            </div>
          </div>

          <!-- Personality -->
          <div class="field-group">
            <label class="field-label">性格特征</label>
            <div class="chip-list">
              <span v-for="(trait, i) in character.personality" :key="i" class="chip-removable">
                {{ trait }}
                <button class="chip-remove" @click="removeTrait(i)">✕</button>
              </span>
              <input
                class="chip-input"
                placeholder="添加特征..."
                @keydown.enter="addTrait(($event.target as HTMLInputElement))"
              />
            </div>
          </div>

          <!-- Relationships -->
          <div class="field-group">
            <label class="field-label">关系网络</label>
            <div class="rel-list">
              <div v-for="(rel, i) in character.relationships" :key="i" class="rel-row">
                <input
                  class="rel-input"
                  :value="rel.target"
                  @input="updateRelationship(i, 'target', ($event.target as HTMLInputElement).value)"
                  placeholder="目标角色"
                />
                <input
                  class="rel-input rel-relation"
                  :value="rel.relation"
                  @input="updateRelationship(i, 'relation', ($event.target as HTMLInputElement).value)"
                  placeholder="关系"
                />
                <button class="rel-remove" @click="removeRelationship(i)">✕</button>
              </div>
              <button class="rel-add" @click="addRelationship">+ 添加关系</button>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="drawer-footer">
          <button class="btn-cancel" @click="$emit('close')">取消</button>
          <button class="btn-save" :disabled="saving" @click="handleSave">
            {{ saving ? '保存中...' : '💾 保存修改' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useScriptStore } from '@/stores/script'
import { updateCharacter as updateCharacterApi } from '@/lib/api'
import { roleLabel } from '@/lib/utils'
import { toast } from '@/stores/toast'
import type { Character, Relationship } from '@/types/character'

const props = defineProps<{
  character: Character
}>()

const emit = defineEmits<{
  close: []
}>()

const store = useScriptStore()
const saving = ref(false)

const roleTypes = [
  { value: 'PROTAGONIST' as const },
  { value: 'DEUTERAGONIST' as const },
  { value: 'ANTAGONIST' as const },
  { value: 'SUPPORTING' as const },
  { value: 'MINOR' as const },
]

const genders = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'UNKNOWN', label: '未知' },
]

function updateField(field: string, value: string) {
  store.updateCharacter(props.character.id, { [field]: value } as Partial<Character>)
}

function addAlias(input: HTMLInputElement) {
  const val = input.value.trim()
  if (val) {
    store.updateCharacter(props.character.id, {
      aliases: [...props.character.aliases, val],
    })
    input.value = ''
  }
}

function removeAlias(idx: number) {
  const aliases = [...props.character.aliases]
  aliases.splice(idx, 1)
  store.updateCharacter(props.character.id, { aliases })
}

function addTrait(input: HTMLInputElement) {
  const val = input.value.trim()
  if (val) {
    store.updateCharacter(props.character.id, {
      personality: [...props.character.personality, val],
    })
    input.value = ''
  }
}

function removeTrait(idx: number) {
  const personality = [...props.character.personality]
  personality.splice(idx, 1)
  store.updateCharacter(props.character.id, { personality })
}

function addRelationship() {
  store.updateCharacter(props.character.id, {
    relationships: [...props.character.relationships, { target: '', relation: '' }],
  })
}

function updateRelationship(idx: number, field: string, value: string) {
  const relationships = props.character.relationships.map((r, i) =>
    i === idx ? { ...r, [field]: value } : r
  )
  store.updateCharacter(props.character.id, { relationships })
}

function removeRelationship(idx: number) {
  const relationships = [...props.character.relationships]
  relationships.splice(idx, 1)
  store.updateCharacter(props.character.id, { relationships })
}

async function handleSave() {
  saving.value = true
  try {
    await updateCharacterApi(props.character.scriptId, props.character.id, {
      canonicalName: props.character.canonicalName,
      roleType: props.character.roleType,
      description: props.character.description,
      aliases: props.character.aliases,
      personality: props.character.personality,
      relationships: props.character.relationships,
    })
    toast.success('角色信息已保存')
    emit('close')
  } catch {
    toast.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.editor-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  z-index: 2000;
  display: flex;
  justify-content: flex-end;
}

.editor-drawer {
  width: 480px;
  max-width: 100vw;
  height: 100vh;
  background: var(--ink-deep-surface);
  border-left: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-float);
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--glass-border);
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.title-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--teal-surface);
  border-radius: 8px;
  font-size: 14px;
}

.drawer-close {
  padding: 4px 8px;
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 16px;
  cursor: pointer;
}

.drawer-close:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.05);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-row {
  display: flex;
  gap: 16px;
}

.field-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.field-input,
.field-textarea {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-primary);
  outline: none;
}

.field-input:focus,
.field-textarea:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.field-textarea {
  resize: vertical;
  min-height: 72px;
}

.flex-1 { flex: 1; }

.chip-select {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.chip-option {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.chip-option:hover {
  border-color: rgba(255, 255, 255, 0.1);
}

.chip-option--active {
  background: var(--teal-surface);
  color: var(--teal-primary);
  border-color: rgba(61, 184, 176, 0.2);
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.chip-removable {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-secondary);
}

.chip-remove {
  font-size: 10px;
  color: var(--text-muted);
  cursor: pointer;
}

.chip-remove:hover {
  color: var(--cinnabar);
}

.chip-input {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--text-primary);
  outline: none;
  min-width: 100px;
}

.chip-input:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.rel-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rel-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.rel-input {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--text-primary);
  outline: none;
  flex: 1;
}

.rel-input:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.rel-relation {
  max-width: 100px;
}

.rel-remove {
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
}

.rel-remove:hover {
  color: var(--cinnabar);
  background: var(--cinnabar-surface);
}

.rel-add {
  padding: 6px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--teal-primary);
  cursor: pointer;
  text-align: left;
}

.rel-add:hover {
  background: var(--teal-surface);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--glass-border);
}

.btn-cancel,
.btn-save {
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cancel {
  color: var(--text-muted);
}

.btn-cancel:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.05);
}

.btn-save {
  color: var(--text-primary);
  background: var(--teal-surface);
  border: 1px solid rgba(61, 184, 176, 0.2);
}

.btn-save:hover:not(:disabled) {
  background: var(--teal-surface-hover);
}

.btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
