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
            @click.stop="confirmDelete(script)"
          >🗑 删除</button>
          <span class="script-date">{{ formatDate(script.createdAt) }}</span>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div v-if="deletingScript" class="modal-overlay" @click.self="cancelDelete">
        <div class="modal-box">
          <div class="modal-icon">⚠️</div>
          <h3 class="modal-title">确认删除</h3>
          <p class="modal-body">
            确定要删除剧本<br />「<strong>{{ deletingScript.title }}</strong>」吗？<br />此操作不可撤销。
          </p>
          <div class="modal-actions">
            <button class="modal-btn modal-btn--cancel" @click="cancelDelete">取消</button>
            <button class="modal-btn modal-btn--danger" @click="handleDelete(deletingScript)">确认删除</button>
          </div>
        </div>
      </div>
    </Teleport>
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
const deletingScript = ref<Script | null>(null)

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

function confirmDelete(script: Script) {
  deletingScript.value = script
}

function cancelDelete() {
  deletingScript.value = null
}

async function handleDelete(script: Script) {
  try {
    await deleteScriptApi(script.id)
    scripts.value = scripts.value.filter((s) => s.id !== script.id)
    toast.success('剧本已删除')
  } catch {
    toast.error('删除失败')
  } finally {
    deletingScript.value = null
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

/* Delete confirmation modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(6px);
  animation: fadeIn 0.2s ease;
}

.modal-box {
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  padding: 32px;
  max-width: 380px;
  width: 90%;
  text-align: center;
  box-shadow: var(--shadow-float);
  animation: scaleIn 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.modal-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.modal-title {
  font-family: var(--font-heading);
  font-size: 18px;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.modal-body {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin-bottom: 24px;
}

.modal-body strong {
  color: var(--text-primary);
  font-weight: 600;
}

.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.modal-btn {
  padding: 8px 24px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.modal-btn--cancel {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-secondary);
}

.modal-btn--cancel:hover {
  background: rgba(255, 255, 255, 0.12);
}

.modal-btn--danger {
  background: var(--cinnabar);
  color: #fff;
}

.modal-btn--danger:hover {
  background: #d44a2a;
  box-shadow: 0 0 16px rgba(194, 59, 34, 0.4);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes scaleIn {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}
</style>
