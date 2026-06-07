<template>
  <div class="list-page">
    <div class="list-header">
      <h1>📋 剧本列表</h1>
      <router-link to="/" class="btn-new">+ 新建剧本</router-link>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="list-state">
      <div class="loading-spinner"></div>
      <p>加载剧本列表...</p>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="list-state">
      <span class="error-icon">⚠️</span>
      <p>{{ error }}</p>
      <button class="btn-retry" @click="fetchScripts">重试</button>
    </div>

    <!-- Empty -->
    <div v-else-if="scripts.length === 0" class="list-state">
      <span class="empty-icon">📭</span>
      <p>还没有剧本</p>
      <router-link to="/" class="btn-new">上传小说开始创作</router-link>
    </div>

    <!-- Script Grid -->
    <div v-else class="script-grid">
      <div
        v-for="script in scripts"
        :key="script.id"
        class="script-card"
        @click="$router.push(`/scripts/${script.id}`)"
      >
        <div class="script-card-header">
          <h3 class="script-card-title">{{ script.title }}</h3>
          <span class="script-status" :class="'status-' + script.status.toLowerCase()">
            {{ statusLabel(script.status) }}
          </span>
        </div>
        <div class="script-card-stats">
          <span>🎬 {{ script.sceneCount }} 场景</span>
          <span>👤 {{ script.characterCount }} 角色</span>
          <span>💬 {{ script.dialogueCount }} 对白</span>
        </div>
        <!-- Progress bar for generating -->
        <div v-if="script.status === 'GENERATING'" class="script-progress">
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: script.progress + '%' }"></div>
          </div>
          <span class="progress-pct">{{ Math.round(script.progress) }}%</span>
        </div>
        <div class="script-card-footer">
          <button
            class="btn-delete"
            @click.stop="handleDelete(script)"
          >删除</button>
          <span class="script-date">{{ formatDate(script.createdAt) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listScripts, deleteScript as deleteScriptApi } from '@/lib/api'
import { toast } from '@/stores/toast'
import type { Script } from '@/types/script'

const router = useRouter()
const scripts = ref<Script[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    COMPLETED: '已完成', GENERATING: '生成中', DRAFT: '草稿', FAILED: '失败',
  }
  return labels[status] ?? status
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

async function fetchScripts() {
  loading.value = true
  error.value = null
  try {
    scripts.value = await listScripts()
  } catch (err: unknown) {
    error.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function handleDelete(script: Script) {
  try {
    await deleteScriptApi(script.id)
    scripts.value = scripts.value.filter((s) => s.id !== script.id)
    toast.success('剧本已删除')
  } catch {
    toast.error('删除失败')
  }
}

onMounted(fetchScripts)
</script>

<style scoped>
.list-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 24px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.list-header h1 {
  font-family: var(--font-heading);
  font-size: 22px;
  color: var(--text-primary);
}

.btn-new {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 18px;
  border-radius: var(--radius-sm);
  background: var(--teal-surface);
  color: var(--teal-primary);
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  border: 1px solid rgba(61, 184, 176, 0.15);
  transition: all 0.2s;
}

.btn-new:hover {
  background: var(--teal-surface-hover);
}

/* States */
.list-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 0;
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

.error-icon { font-size: 28px; }
.empty-icon { font-size: 36px; }

.btn-retry {
  padding: 6px 16px;
  border-radius: var(--radius-sm);
  background: var(--teal-surface);
  color: var(--teal-primary);
  font-size: 13px;
  cursor: pointer;
}

/* Grid */
.script-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.script-card {
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.02);
  padding: 18px;
  cursor: pointer;
  transition: all 0.3s;
}

.script-card:hover {
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  transform: translateY(-1px);
  box-shadow: var(--shadow-glass);
}

.script-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.script-card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.script-status {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.status-completed { background: var(--teal-surface); color: var(--teal-primary); }
.status-generating { background: var(--warm-gold-surface); color: var(--warm-gold); }
.status-draft { background: rgba(255, 255, 255, 0.05); color: var(--text-muted); }
.status-failed { background: var(--cinnabar-surface); color: var(--cinnabar); }

.script-card-stats {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.script-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.progress-track {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--teal-primary), var(--teal-hover));
  transition: width 0.5s;
}

.progress-pct {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--teal-primary);
}

.script-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.btn-delete {
  font-size: 11px;
  color: var(--text-muted);
  padding: 3px 10px;
  border-radius: 4px;
  cursor: pointer;
}

.btn-delete:hover {
  color: var(--cinnabar);
  background: var(--cinnabar-surface);
}

.script-date {
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.5;
}
</style>
