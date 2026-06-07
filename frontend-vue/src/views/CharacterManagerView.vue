<template>
  <div class="char-page">
    <!-- Loading -->
    <div v-if="loading" class="page-state">
      <div class="loading-spinner"></div>
      <p>加载角色数据...</p>
    </div>

    <!-- Empty -->
    <div v-else-if="characters.length === 0" class="page-state">
      <span class="empty-icon">👤</span>
      <p>暂未提取到角色</p>
    </div>

    <template v-else>
      <!-- Toggle bar -->
      <div class="view-toggle">
        <h2>角色管理 ({{ characters.length }})</h2>
        <div class="toggle-btns">
          <button
            class="toggle-btn"
            :class="{ 'toggle-btn--active': viewMode === 'grid' }"
            @click="viewMode = 'grid'"
          >🃏 卡片</button>
          <button
            class="toggle-btn"
            :class="{ 'toggle-btn--active': viewMode === 'graph' }"
            @click="viewMode = 'graph'"
          >🕸️ 关系图</button>
        </div>
      </div>

      <!-- Grid View -->
      <div v-if="viewMode === 'grid'" class="char-grid">
        <CharacterCard
          v-for="char in characters"
          :key="char.id"
          :character="char"
        />
      </div>

      <!-- Graph View -->
      <RelationshipGraph
        v-else
        :characters="characters"
        :selected-id="store.selectedCharacterId"
        @select="store.selectCharacter"
      />

      <!-- Character Editor Drawer -->
      <CharacterEditor
        v-if="selectedCharacter"
        :character="selectedCharacter"
        @close="store.selectCharacter(null)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useScriptStore } from '@/stores/script'
import { getScript } from '@/lib/api'
import CharacterCard from '@/components/character/CharacterCard.vue'
import CharacterEditor from '@/components/character/CharacterEditor.vue'
import RelationshipGraph from '@/components/character/RelationshipGraph.vue'

const route = useRoute()
const scriptId = Number(route.params.id)
const store = useScriptStore()

const loading = ref(true)
const viewMode = ref<'grid' | 'graph'>('grid')

const characters = computed(() => store.script?.characters ?? [])
const selectedCharacter = computed(() => {
  if (!store.selectedCharacterId || !store.script) return null
  return store.script.characters.find((c) => c.id === store.selectedCharacterId) ?? null
})

// Auto-open editor for character specified in query param
watch(() => route.query.edit, (editId) => {
  if (editId && store.script) {
    const cid = Number(editId)
    const exists = store.script.characters.some((c) => c.id === cid)
    if (exists) store.selectCharacter(cid)
  }
})

onMounted(async () => {
  try {
    await getScript(scriptId).then(store.setScript)
    // After loading, check if we need to open a specific character
    const editParam = route.query.edit
    if (editParam) {
      const cid = Number(editParam)
      const exists = store.script?.characters.some((c) => c.id === cid)
      if (exists) store.selectCharacter(cid)
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.char-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}

.page-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 0;
  color: var(--text-muted);
  font-size: 14px;
}

.loading-spinner {
  width: 28px;
  height: 28px;
  border: 2px solid rgba(61, 184, 176, 0.2);
  border-top-color: var(--teal-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty-icon {
  font-size: 40px;
}

.view-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.view-toggle h2 {
  font-family: var(--font-heading);
  font-size: 20px;
  color: var(--text-primary);
}

.toggle-btns {
  display: flex;
  gap: 2px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 8px;
  padding: 2px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.toggle-btn {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn:hover {
  color: var(--text-primary);
}

.toggle-btn--active {
  background: var(--teal-surface);
  color: var(--teal-primary);
}

.char-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
</style>
