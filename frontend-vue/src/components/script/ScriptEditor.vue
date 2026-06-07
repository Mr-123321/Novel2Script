<template>
  <div class="editor-layout">
    <!-- Left sidebar: Scene list -->
    <aside class="editor-sidebar" :class="{ 'sidebar-hidden': !leftOpen }">
      <SceneList :scenes="scenes" />
    </aside>

    <!-- Center: Script content -->
    <main class="editor-main" ref="mainRef">
      <!-- Top bar -->
      <div class="editor-toolbar">
        <div class="toolbar-left">
          <button class="toolbar-icon-btn" @click="leftOpen = !leftOpen" title="切换场景列表">
            ◀
          </button>
          <span class="script-title">{{ script?.title ?? '剧本' }}</span>
        </div>

        <!-- Mode toggle -->
        <div class="mode-toggle">
          <button
            class="mode-btn"
            :class="{ 'mode-btn--active': !editMode }"
            @click="editMode = false"
          >📖 阅读</button>
          <button
            class="mode-btn"
            :class="{ 'mode-btn--active': editMode }"
            @click="editMode = true"
          >✏️ 编辑</button>
        </div>

        <!-- Download dropdown (only when completed) -->
        <div v-if="script?.status === 'COMPLETED'" class="toolbar-downloads">
          <button class="download-btn" @click="downloadOpen = !downloadOpen">
            📥 下载
          </button>
          <div v-if="downloadOpen" class="download-menu">
            <button class="download-menu-item" @click="handleDownload('txt')">
              <span class="menu-item-icon">📄</span>
              <span class="menu-item-label">TXT 文本</span>
              <span class="menu-item-hint">纯文本剧本</span>
            </button>
            <button class="download-menu-item" @click="handleDownload('md')">
              <span class="menu-item-icon">📝</span>
              <span class="menu-item-label">Markdown</span>
              <span class="menu-item-hint">带格式标记</span>
            </button>
            <button class="download-menu-item" @click="handleDownload('yaml')">
              <span class="menu-item-icon">📋</span>
              <span class="menu-item-label">YAML</span>
              <span class="menu-item-hint">结构化数据</span>
            </button>
          </div>
        </div>

        <button class="toolbar-icon-btn" @click="rightOpen = !rightOpen" title="切换角色面板">
          ▶
        </button>
      </div>

      <div class="script-scroll">
        <!-- Insertions before first scene (position 0) -->
        <div
          v-for="ins in insertionsAtStart"
          :key="'ins-' + ins.id"
          class="plot-insertion"
        >
          <div class="insertion-header">
            <span class="insertion-label">📝 情节插入</span>
            <button class="insertion-delete" @click="$emit('delete-insertion', ins.id)">✕</button>
          </div>
          <p class="insertion-text">{{ ins.text }}</p>
        </div>

        <!-- Scene cards -->
        <div
          v-for="(scene, idx) in scenes"
          :key="scene.id"
          :ref="(el) => setSceneRef(scene.id, el)"
        >
          <SceneCard
            :scene="scene"
            :selected="selectedSceneId === scene.id"
            :editable="editMode"
          />
          <!-- Insertions after this scene -->
          <div
            v-for="ins in getInsertionsAfter(idx)"
            :key="'ins-' + ins.id"
            class="plot-insertion"
          >
            <div class="insertion-header">
              <span class="insertion-label">📝 情节插入</span>
              <button class="insertion-delete" @click="$emit('delete-insertion', ins.id)">✕</button>
            </div>
            <p class="insertion-text">{{ ins.text }}</p>
          </div>
        </div>

        <!-- Empty state -->
        <div v-if="scenes.length === 0" class="empty-state">
          <div class="loading-spinner"></div>
          <p>暂无场景，请等待 AI 生成完成</p>
        </div>
      </div>
    </main>

    <!-- Right sidebar: Character panel -->
    <aside class="editor-sidebar editor-sidebar--right" :class="{ 'sidebar-hidden': !rightOpen }">
      <CharacterPanel
        :characters="script?.characters ?? []"
        :selected-scene="selectedScene"
      />
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useScriptStore } from '@/stores/script'
import { downloadScriptYaml, downloadScriptTxt, downloadScriptMd } from '@/lib/api'
import { generateMdContent } from '@/lib/utils'
import { toast } from '@/stores/toast'
import SceneList from './SceneList.vue'
import SceneCard from './SceneCard.vue'
import CharacterPanel from './CharacterPanel.vue'
import type { PlotInsertion } from '@/types/script'

const store = useScriptStore()

const leftOpen = ref(true)
const rightOpen = ref(true)
const editMode = ref(false)
const downloadOpen = ref(false)
const mainRef = ref<HTMLElement | null>(null)
const sceneRefs = new Map<number, HTMLElement>()
const hasScrolled = ref(false)

function setSceneRef(id: number, el: unknown) {
  if (el instanceof HTMLElement) {
    sceneRefs.set(id, el)
  } else {
    sceneRefs.delete(id)
  }
}

const script = computed(() => store.script)
const scenes = computed(() => store.script?.scenes ?? [])
const selectedSceneId = computed(() => store.selectedSceneId)
const selectedScene = computed(() => store.selectedScene)

const insertions = computed(() => store.script?.plotInsertions ?? [])

const insertionsAtStart = computed(() =>
  insertions.value.filter((ins) => ins.position === 0)
)

function getInsertionsAfter(idx: number): PlotInsertion[] {
  return insertions.value.filter((ins) => ins.position === idx + 1)
}

// ── Download handlers ──

function triggerBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

const FORMAT_LABELS: Record<string, string> = { txt: 'TXT', md: 'Markdown', yaml: 'YAML' }

async function handleDownload(format: 'txt' | 'md' | 'yaml') {
  downloadOpen.value = false
  if (!store.script?.id) return
  const sid = store.script.id
  const title = store.script.title?.replace(/[\\\\/:*?\"<>|]/g, '_') ?? 'script'
  const label = FORMAT_LABELS[format]

  try {
    if (format === 'txt') {
      const blob = await downloadScriptTxt(sid)
      triggerBlobDownload(blob, `${title}.txt`)
    } else if (format === 'md') {
      let blob: Blob
      try {
        blob = await downloadScriptMd(sid)
      } catch {
        // Fallback: generate Markdown on the client side
        const md = generateMdContent({
          title: store.script.title ?? '未命名剧本',
          scenes: store.script.scenes ?? [],
          characters: (store.script.characters ?? []).map((c) => ({
            id: c.id,
            canonicalName: c.canonicalName,
          })),
        })
        blob = new Blob([md], { type: 'text/markdown; charset=UTF-8' })
      }
      triggerBlobDownload(blob, `${title}.md`)
    } else if (format === 'yaml') {
      let blob: Blob
      try {
        blob = await downloadScriptYaml(sid)
      } catch {
        const yaml = store.script.yamlContent
        if (yaml) {
          blob = new Blob([yaml], { type: 'text/yaml' })
        } else {
          throw new Error('no yaml')
        }
      }
      triggerBlobDownload(blob, `${title}.yaml`)
    }
    toast.success(`${label} 下载开始`)
  } catch {
    toast.error(`${label} 下载失败，请稍后重试`)
  }
}

// Click outside to close dropdown
function onDocumentClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.toolbar-downloads')) {
    downloadOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
})

// Scroll to selected scene on mount
onMounted(() => {
  setTimeout(() => {
    if (store.selectedSceneId) {
      const el = sceneRefs.get(store.selectedSceneId)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' })
        hasScrolled.value = true
        return
      }
    }
    if (!hasScrolled.value && mainRef.value) {
      mainRef.value.scrollTop = 0
    }
  }, 100)
})

// Entering edit mode: default to first scene, scroll to top
watch(editMode, (val) => {
  if (val) {
    if (!store.selectedSceneId && scenes.value.length > 0) {
      store.selectScene(scenes.value[0].id)
    }
    nextTick(() => {
      if (mainRef.value) {
        mainRef.value.scrollTop = 0
      }
    })
  }
})

// Scroll to scene when selected from list
watch(() => store.selectedSceneId, (id) => {
  if (id != null) {
    nextTick(() => {
      const el = sceneRefs.get(id)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    })
  }
})
</script>

<style scoped>
.editor-layout {
  display: flex;
  height: calc(100vh - var(--topbar-height));
}

.editor-sidebar {
  width: 260px;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.02);
  overflow: hidden;
  transition: width 0.3s;
  flex-shrink: 0;
}

.editor-sidebar--right {
  border-right: none;
  border-left: 1px solid rgba(255, 255, 255, 0.05);
}

.sidebar-hidden {
  width: 0;
  border: none;
}

.editor-main {
  flex: 1;
  overflow-y: auto;
}

.editor-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(15, 17, 21, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-icon-btn {
  padding: 4px 6px;
  border-radius: 6px;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.toolbar-icon-btn:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.06);
}

.script-title {
  font-size: 12px;
  color: var(--text-muted);
  opacity: 0.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

/* Mode toggle */
.mode-toggle {
  display: flex;
  gap: 1px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 8px;
  padding: 2px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.mode-btn:hover {
  color: var(--text-primary);
}

.mode-btn--active {
  background: var(--teal-surface);
  color: var(--teal-primary);
  box-shadow: inset 0 1px 0 rgba(61, 184, 176, 0.08);
}

/* Download buttons */
.toolbar-downloads {
  position: relative;
}

.download-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.download-btn:hover {
  color: var(--teal-primary);
  background: var(--teal-surface);
  border-color: rgba(61, 184, 176, 0.15);
}

/* Dropdown menu */
.download-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 180px;
  background: var(--ink-deep-elevated);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 4px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.03) inset;
  backdrop-filter: blur(16px);
  z-index: 100;
  animation: dropdownIn 0.15s ease-out;
}

@keyframes dropdownIn {
  from { opacity: 0; transform: translateY(-4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.download-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
}

.download-menu-item:hover {
  background: var(--teal-surface);
  color: var(--text-primary);
}

.menu-item-icon {
  font-size: 16px;
  flex-shrink: 0;
  width: 22px;
  text-align: center;
}

.menu-item-label {
  font-weight: 500;
  flex: 1;
}

.menu-item-hint {
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0.6;
}

/* Script scroll area */
.script-scroll {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Plot Insertions */
.plot-insertion {
  border-radius: 10px;
  border: 1px dashed rgba(212, 168, 83, 0.2);
  background: rgba(212, 168, 83, 0.03);
  padding: 12px 16px;
  position: relative;
}

.plot-insertion:hover .insertion-delete {
  opacity: 1;
}

.insertion-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.insertion-label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: rgba(212, 168, 83, 0.5);
  font-weight: 500;
}

.insertion-delete {
  padding: 2px;
  border-radius: 4px;
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s;
}

.insertion-delete:hover {
  color: var(--cinnabar);
  background: rgba(194, 59, 34, 0.1);
}

.insertion-text {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  white-space: pre-wrap;
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 0;
  color: var(--text-muted);
  font-size: 14px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid rgba(61, 184, 176, 0.2);
  border-top-color: var(--teal-primary);
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
