<template>
  <div class="character-panel">
    <!-- Scene Characters -->
    <div class="panel-section" v-if="sceneCharacters.length > 0">
      <h4 class="section-title">🎭 本场角色</h4>
      <div class="character-chips">
        <button
          v-for="char in sceneCharacters"
          :key="char.id"
          class="char-chip"
          :class="{ 'char-chip--active': store.selectedCharacterId === char.id }"
          @click="store.selectCharacter(char.id)"
          @dblclick="goToCharacterEdit(char.id)"
          title="双击跳转到角色编辑"
        >
          <span class="role-dot" :class="'role-' + char.roleType.toLowerCase()"></span>
          {{ char.canonicalName }}
        </button>
      </div>
    </div>

    <!-- All Characters -->
    <div class="panel-section">
      <h4 class="section-title">👥 全部角色</h4>
      <div class="character-chips" v-if="allCharacters.length > 0">
        <button
          v-for="char in allCharacters"
          :key="char.id"
          class="char-chip"
          :class="{ 'char-chip--active': store.selectedCharacterId === char.id }"
          @click="store.selectCharacter(char.id)"
          @dblclick="goToCharacterEdit(char.id)"
          title="双击跳转到角色编辑"
        >
          <span class="role-dot" :class="'role-' + char.roleType.toLowerCase()"></span>
          {{ char.canonicalName }}
          <span class="role-badge">{{ roleLabel(char.roleType) }}</span>
        </button>
      </div>
      <p v-else class="empty-hint">暂无角色数据</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useScriptStore } from '@/stores/script'
import { roleLabel } from '@/lib/utils'
import type { Character } from '@/types/character'
import type { Scene } from '@/types/script'

const props = defineProps<{
  characters: Character[]
  selectedScene: Scene | null
}>()

const router = useRouter()
const store = useScriptStore()

function goToCharacterEdit(characterId: number) {
  const scriptId = store.script?.id
  if (!scriptId) return
  router.push({ name: 'characters', params: { id: scriptId }, query: { edit: characterId } })
}

const allCharacters = computed(() => props.characters)

const sceneCharacters = computed(() => {
  if (!props.selectedScene?.characterIds) return []
  const charMap = new Map(props.characters.map((c) => [c.id, c]))
  return props.selectedScene.characterIds
    .map((id) => charMap.get(id))
    .filter((c): c is Character => c !== undefined)
})
</script>

<style scoped>
.character-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.panel-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  font-family: var(--font-heading);
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 600;
}

.character-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.char-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.char-chip:hover {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-primary);
}

.char-chip--active {
  background: var(--teal-surface);
  border-color: rgba(61, 184, 176, 0.2);
  color: var(--teal-primary);
  box-shadow: inset 0 1px 0 rgba(61, 184, 176, 0.08);
}

.role-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
}

.role-protagonist { background: var(--cinnabar); }
.role-deuteragonist { background: var(--warm-gold); }
.role-antagonist { background: #9b59b6; }
.role-supporting { background: var(--teal-primary); }
.role-minor { background: var(--text-muted); }

.role-badge {
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0.7;
}

.empty-hint {
  font-size: 12px;
  color: var(--text-muted);
  font-style: italic;
}
</style>
