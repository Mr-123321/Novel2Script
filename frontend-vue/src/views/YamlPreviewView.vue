<template>
  <div class="yaml-page">
    <div class="yaml-header">
      <div class="yaml-title-row">
        <router-link :to="`/scripts/${scriptId}`" class="back-link">← 返回编辑</router-link>
        <h1>YAML 预览</h1>
      </div>
      <div class="yaml-actions">
        <button class="action-btn" @click="handleCopy" :disabled="!yamlContent">
          {{ copied ? '✅ 已复制' : '📋 复制' }}
        </button>
        <button class="action-btn" @click="handleDownload" :disabled="!yamlContent">
          📥 下载
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="yaml-state">
      <div class="loading-spinner"></div>
      <p>加载 YAML 内容...</p>
    </div>

    <!-- Error (not yet generated) -->
    <div v-else-if="yamlMessage" class="yaml-state">
      <span class="yaml-empty-icon">📄</span>
      <p>{{ yamlMessage }}</p>
      <router-link :to="`/scripts/${scriptId}`" class="action-link">返回剧本编辑 →</router-link>
    </div>

    <!-- YAML Content -->
    <div v-else-if="yamlContent" class="yaml-editor-wrap">
      <MonacoEditor
        v-model="yamlContent"
        language="yaml"
        :read-only="true"
        theme="vs-dark"
      />
    </div>

    <!-- Empty -->
    <div v-else class="yaml-state">
      <span class="yaml-empty-icon">📭</span>
      <p>YAML 内容为空</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getScriptYaml, downloadScriptYaml } from '@/lib/api'
import { toast } from '@/stores/toast'
import MonacoEditor from '@/components/editor/MonacoEditor.vue'

const route = useRoute()
const scriptId = Number(route.params.id)

const yamlContent = ref('')
const yamlMessage = ref<string | null>(null)
const loading = ref(true)
const copied = ref(false)

async function fetchYaml() {
  loading.value = true
  try {
    const result = await getScriptYaml(scriptId)
    if ('yaml' in result && result.yaml) {
      yamlContent.value = result.yaml
    } else if ('message' in result) {
      yamlMessage.value = result.message ?? 'YAML 尚未生成'
    }
  } catch (err: unknown) {
    yamlMessage.value = '加载失败'
  } finally {
    loading.value = false
  }
}

async function handleCopy() {
  if (!yamlContent.value) return
  try {
    await navigator.clipboard.writeText(yamlContent.value)
    copied.value = true
    toast.success('已复制到剪贴板')
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    toast.error('复制失败')
  }
}

async function handleDownload() {
  if (!yamlContent.value) return
  try {
    const blob = await downloadScriptYaml(scriptId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `script-${scriptId}.yaml`
    a.click()
    URL.revokeObjectURL(url)
    toast.success('下载开始')
  } catch {
    // Fallback: download from content
    const blob = new Blob([yamlContent.value], { type: 'text/yaml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `script-${scriptId}.yaml`
    a.click()
    URL.revokeObjectURL(url)
  }
}

onMounted(fetchYaml)
</script>

<style scoped>
.yaml-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--topbar-height));
}

.yaml-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--glass-border);
  background: rgba(15, 17, 21, 0.6);
  backdrop-filter: blur(8px);
}

.yaml-title-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.yaml-title-row h1 {
  font-family: var(--font-heading);
  font-size: 18px;
  color: var(--text-primary);
}

.back-link {
  font-size: 13px;
  color: var(--text-muted);
  text-decoration: none;
}

.back-link:hover {
  color: var(--teal-primary);
}

.yaml-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--glass-border);
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  color: var(--teal-primary);
  background: var(--teal-surface);
  border-color: rgba(61, 184, 176, 0.2);
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* States */
.yaml-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 20px;
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

.yaml-empty-icon {
  font-size: 40px;
}

.action-link {
  font-size: 13px;
  color: var(--teal-primary);
  text-decoration: none;
}

/* Editor */
.yaml-editor-wrap {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}
</style>
