<template>
  <div class="mermaid-panel">
    <div v-if="isLoading" class="mermaid-loading">
      <div class="loading-spinner"></div>
      <p>加载工作流图...</p>
    </div>
    <div v-else-if="error" class="mermaid-error">
      <span>❌</span>
      <p>{{ error }}</p>
      <button @click="fetchMermaid">重试</button>
    </div>
    <pre v-else-if="mermaid" class="mermaid-code">{{ mermaid }}</pre>
    <div v-else class="mermaid-empty">
      <span>📊</span>
      <p>暂无工作流数据</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getWorkflowMermaid } from '@/lib/api'

const props = defineProps<{
  scriptId: number
}>()

const mermaid = ref('')
const isLoading = ref(false)
const error = ref<string | null>(null)

async function fetchMermaid() {
  isLoading.value = true
  error.value = null
  try {
    const result = await getWorkflowMermaid(props.scriptId)
    mermaid.value = result.mermaid
  } catch (err: unknown) {
    error.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    isLoading.value = false
  }
}

onMounted(fetchMermaid)
</script>

<style scoped>
.mermaid-panel {
  margin-top: 16px;
  padding: 16px;
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
}

.mermaid-loading,
.mermaid-error,
.mermaid-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
  color: var(--text-muted);
  font-size: 13px;
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

.mermaid-error {
  color: var(--cinnabar);
}

.mermaid-error button {
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  background: var(--cinnabar-surface);
  color: var(--cinnabar);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.mermaid-error button:hover {
  background: var(--cinnabar-surface-hover);
}

.mermaid-code {
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}
</style>
