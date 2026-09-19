<template>
  <div class="editor-page">
    <!-- Generation progress overlay -->
    <div v-if="showProgressOverlay" class="progress-overlay">
      <InkProgress
        v-if="!generationError"
        :percent="displayProgress"
        :message="statusText"
      />
    </div>

    <!-- Partial generation banner (finished, but some scenes are 待补全) -->
    <div v-if="!generationError && script?.status === 'PARTIAL'" class="partial-banner">
      <span class="banner-icon">✎</span>
      <div class="banner-body">
        <p class="banner-title">生成完成，但有部分场景未能生成</p>
        <p class="banner-detail">
          对白缺失 {{ failedDialogueScenes }} 个场景<span v-if="failedActionScenes > 0">，动作缺失 {{ failedActionScenes }} 个场景</span>。
          系统未编造任何台词或动作，请人工补全后再使用。
        </p>
      </div>
    </div>

    <!-- Script failed banner (already failed on load) -->
    <div v-if="!generationError && script?.status === 'FAILED'" class="failed-banner">
      <span class="banner-icon">⚠️</span>
      <div class="banner-body">
        <p class="banner-title">剧本生成失败</p>
        <p v-if="script?.workflowState?.error" class="banner-detail">
          {{ (script.workflowState as Record<string, unknown>).error }}
        </p>
      </div>
      <router-link to="/scripts/new" class="banner-action">重新生成</router-link>
    </div>

    <!-- Generation error banner (SSE error) -->
    <div v-if="generationError" class="failed-banner">
      <span class="banner-icon">⚠️</span>
      <p>{{ generationError }}</p>
      <router-link to="/scripts/new" class="banner-action">重新生成</router-link>
    </div>

    <!-- Main editor -->
    <ScriptEditor v-if="script && script.status !== 'GENERATING'" :script-id="scriptId" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useScriptStore } from '@/stores/script'
import { useSse } from '@/composables/useSse'
import { getScript } from '@/lib/api'
import ScriptEditor from '@/components/script/ScriptEditor.vue'
import InkProgress from '@/components/InkProgress.vue'

const route = useRoute()
const scriptId = Number(route.params.id)
const store = useScriptStore()

const generationError = ref<string | null>(null)
const displayProgress = ref(0)
const progressVisible = ref(true)
const startTime = ref(Date.now())
const resolved = ref(false)

const TOTAL_SEC = 240
const CAP = 95

// Progress simulation (4-minute exponential)
let progressTimer: ReturnType<typeof setInterval> | null = null
let hideProgressTimeout: ReturnType<typeof setTimeout> | null = null

function startProgressSimulation() {
  // Cancel any pending hide-progress timeout from a previous script load
  if (hideProgressTimeout) {
    clearTimeout(hideProgressTimeout)
    hideProgressTimeout = null
  }
  // Reset error state — new generation should clear old errors
  generationError.value = null

  startTime.value = Date.now()
  resolved.value = false
  progressVisible.value = true
  displayProgress.value = 0

  const tick = () => {
    if (resolved.value) return
    const elapsed = (Date.now() - startTime.value) / 1000
    const simulated = 5 + (CAP - 5) * (1 - Math.exp(-elapsed / 55))

    const ssePct = store.progress?.overallProgress ?? 0
    const current = Math.max(
      Math.min(simulated, CAP),
      Math.min(ssePct, CAP)
    )
    displayProgress.value = current
  }

  tick()
  progressTimer = setInterval(tick, 200)
}

function stopProgressSimulation() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

// Compute status text
const statusText = computed(() => {
  const p = displayProgress.value
  if (p < 30) return 'AI 正在分析小说结构…'
  if (p < 55) return '正在提取角色与情节…'
  if (p < 75) return '正在切分场景…'
  if (p < 90) return '正在生成对话与动作…'
  return '正在编排最终剧本…'
})

// Fetch script
onMounted(async () => {
  store.setIsLoading(true)
  try {
    await getScript(scriptId).then(store.setScript)
  } catch {
    // Error handled by store
  }
})

// SSE connection
const sseEnabled = computed(() => store.script?.status === 'GENERATING' && !generationError.value)

useSse({
  scriptId: computed(() => scriptId),
  enabled: sseEnabled,
  onProgress: (data) => {
    store.updateProgress(data)
  },
  onComplete: () => {
    getScript(scriptId).then(store.setScript)
  },
  onError: () => {
    getScript(scriptId).then(store.setScript)
    generationError.value = '剧本生成失败，请返回重新生成'
  },
})

// Progress animation
watch(() => store.script?.status, (status) => {
  if (status === 'GENERATING') {
    startProgressSimulation()
  }
  if (status === 'COMPLETED' || status === 'PARTIAL' || status === 'FAILED') {
    resolved.value = true
    displayProgress.value = 100
    hideProgressTimeout = setTimeout(() => {
      progressVisible.value = false
      hideProgressTimeout = null
    }, 600)
    stopProgressSimulation()
  }
}, { immediate: true })

onUnmounted(() => {
  stopProgressSimulation()
  if (hideProgressTimeout) {
    clearTimeout(hideProgressTimeout)
    hideProgressTimeout = null
  }
})

const script = computed(() => store.script)

function readWarningCount(key: string): number {
  const ws = script.value?.workflowState as Record<string, unknown> | undefined
  const raw = ws?.[key]
  const n = typeof raw === 'number' ? raw : Number(raw)
  return Number.isFinite(n) ? n : 0
}

const failedDialogueScenes = computed(() => readWarningCount('failedDialogueScenes'))
const failedActionScenes = computed(() => readWarningCount('failedActionScenes'))
const showProgressOverlay = computed(() =>
  script.value?.status === 'GENERATING' && !generationError.value && progressVisible.value
)
</script>

<style scoped>
.editor-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--topbar-height));
}

.progress-overlay {
  position: fixed;
  inset: 0;
  z-index: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  padding-top: var(--topbar-height);
}

.failed-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: var(--cinnabar-surface);
  border-bottom: 1px solid rgba(194, 59, 34, 0.2);
  color: var(--cinnabar);
  font-size: 13px;
}

.partial-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 20px;
  background: var(--warm-gold-surface);
  border-bottom: 1px solid rgba(212, 168, 83, 0.28);
  color: var(--warm-gold-light);
  font-size: 13px;
}

.partial-banner .banner-title {
  color: var(--warm-gold-light);
}

.partial-banner .banner-detail {
  color: var(--text-secondary);
}

.banner-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.banner-body {
  flex: 1;
}

.banner-title {
  font-weight: 600;
}

.banner-detail {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 2px;
}

.banner-action {
  padding: 4px 14px;
  border-radius: 6px;
  background: rgba(194, 59, 34, 0.15);
  color: var(--cinnabar);
  font-size: 12px;
  text-decoration: none;
  white-space: nowrap;
}

.banner-action:hover {
  background: rgba(194, 59, 34, 0.25);
}
</style>
