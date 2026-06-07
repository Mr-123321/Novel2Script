<template>
  <div class="home-layout">
    <!-- Sidebar: Recent Scripts -->
    <aside class="home-sidebar">
      <div class="sidebar-header">
        <h3>📜 最近剧本</h3>
        <router-link to="/scripts" class="view-all">查看全部 →</router-link>
      </div>
      <div v-if="recentScripts.length > 0" class="recent-list">
        <div
          v-for="script in recentScripts"
          :key="script.id"
          class="recent-item"
          @click="$router.push(`/scripts/${script.id}`)"
        >
          <span class="recent-title">{{ script.title }}</span>
          <div class="recent-right">
            <span class="recent-status" :class="'status-' + script.status.toLowerCase()">
              {{ statusLabel(script.status) }}
            </span>
            <button
              class="recent-delete-btn"
              @click.stop="confirmDelete(script)"
              title="删除剧本"
            >✕</button>
          </div>
        </div>
      </div>

      <!-- Delete Confirmation Modal -->
      <Teleport to="body">
        <div v-if="deletingId !== null" class="modal-overlay" @click.self="deletingId = null">
          <div class="modal-box">
            <div class="modal-icon">⚠️</div>
            <h3 class="modal-title">确认删除</h3>
            <p class="modal-body">
              确定要删除剧本<br />「<strong>{{ deletingTitle }}</strong>」吗？<br />此操作不可撤销。
            </p>
            <div class="modal-actions">
              <button class="modal-btn modal-btn--cancel" @click="deletingId = null">取消</button>
              <button class="modal-btn modal-btn--danger" @click="handleDelete">确认删除</button>
            </div>
          </div>
        </div>
      </Teleport>
      <div v-else class="sidebar-empty">
        <p>暂无剧本</p>
        <p>上传小说开始创作</p>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="home-main">
      <div class="hero-section stagger-fade">
        <!-- Logo Title -->
        <h1 class="hero-title">
          <span class="title-char">落</span>
          <span class="title-char">墨</span>
          <span class="title-char">成</span>
          <span class="title-char">戏</span>
        </h1>
        <p class="hero-subtitle">AI 驱动的剧本转换引擎 — 从小说到剧本，一笔落墨</p>

        <!-- Upload Area -->
        <div class="upload-container">
          <UploadZone />
        </div>

        <!-- Feature Cards -->
        <div class="feature-grid">
          <div class="feature-card">
            <div class="feature-icon-wrap">
              <svg class="feature-svg" viewBox="0 0 48 48" fill="none">
                <rect x="8" y="6" width="24" height="32" rx="3" stroke="currentColor" stroke-width="1.5" fill="none"/>
                <line x1="14" y1="14" x2="26" y2="14" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <line x1="14" y1="19" x2="26" y2="19" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <line x1="14" y1="24" x2="22" y2="24" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <circle cx="36" cy="12" r="5" stroke="currentColor" stroke-width="1.3" fill="none"/>
                <path d="M33.5 12 l2 2 l4 -4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="36" cy="28" r="6" stroke="currentColor" stroke-width="1.2" fill="none"/>
                <path d="M36 24 v0 m-1 2 a3 3 0 1 0 2 0" stroke="currentColor" stroke-width="1" fill="none" opacity="0.6"/>
              </svg>
            </div>
            <h4>智能解析</h4>
            <p>自动识别章节结构、人物关系与剧情走向</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon-wrap">
              <svg class="feature-svg" viewBox="0 0 48 48" fill="none">
                <rect x="6" y="8" width="36" height="26" rx="2" stroke="currentColor" stroke-width="1.5" fill="none"/>
                <rect x="10" y="12" width="13" height="11" rx="1" stroke="currentColor" stroke-width="1.1" fill="none"/>
                <rect x="25" y="12" width="13" height="11" rx="1" stroke="currentColor" stroke-width="1.1" fill="none"/>
                <rect x="10" y="26" width="13" height="5" rx="1" stroke="currentColor" stroke-width="1.1" fill="none" opacity="0.5"/>
                <rect x="25" y="26" width="13" height="5" rx="1" stroke="currentColor" stroke-width="1.1" fill="none" opacity="0.5"/>
                <circle cx="15" cy="40" r="2" stroke="currentColor" stroke-width="1" fill="none"/>
                <circle cx="24" cy="40" r="2" stroke="currentColor" stroke-width="1" fill="none"/>
                <circle cx="33" cy="40" r="2" stroke="currentColor" stroke-width="1" fill="none"/>
              </svg>
            </div>
            <h4>场景切分</h4>
            <p>AI 将小说按时空切分为标准剧本场景</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon-wrap">
              <svg class="feature-svg" viewBox="0 0 48 48" fill="none">
                <path d="M6 10 h36 l-3 6 H9 l-3 -6 z" stroke="currentColor" stroke-width="1.3" fill="none" stroke-linejoin="round"/>
                <path d="M8 16 h32 l-4 20 H12 l-4 -20 z" stroke="currentColor" stroke-width="1.3" fill="none" stroke-linejoin="round"/>
                <line x1="16" y1="16" x2="16" y2="36" stroke="currentColor" stroke-width="1" opacity="0.4"/>
                <line x1="32" y1="16" x2="32" y2="36" stroke="currentColor" stroke-width="1" opacity="0.4"/>
                <circle cx="24" cy="26" r="5" stroke="currentColor" stroke-width="1.2" fill="none"/>
                <circle cx="24" cy="26" r="2" fill="currentColor" opacity="0.5"/>
                <path d="M14 42 h20" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </div>
            <h4>剧本生成</h4>
            <p>AI 驱动对白与动作，一键导出标准格式</p>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listScripts, deleteScript } from '@/lib/api'
import { toast } from '@/stores/toast'
import UploadZone from '@/components/novel/UploadZone.vue'
import type { Script } from '@/types/script'

const recentScripts = ref<Script[]>([])
const deletingId = ref<number | null>(null)

const deletingTitle = computed(() => {
  const s = recentScripts.value.find((s) => s.id === deletingId.value)
  return s?.title ?? ''
})

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    COMPLETED: '已完成',
    GENERATING: '生成中',
    DRAFT: '草稿',
    FAILED: '失败',
  }
  return labels[status] ?? status
}

function confirmDelete(script: Script) {
  deletingId.value = script.id
}

async function handleDelete() {
  if (deletingId.value === null) return
  try {
    await deleteScript(deletingId.value)
    recentScripts.value = recentScripts.value.filter((s) => s.id !== deletingId.value)
    toast.success('剧本已删除')
  } catch {
    toast.error('删除失败')
  } finally {
    deletingId.value = null
  }
}

onMounted(async () => {
  try {
    const scripts = await listScripts()
    recentScripts.value = scripts.slice(0, 6)
  } catch {
    // Silently fail — recent scripts are non-critical
  }
})
</script>

<style scoped>
.home-layout {
  display: flex;
  min-height: calc(100vh - var(--topbar-height));
}

/* Sidebar */
.home-sidebar {
  width: 280px;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.01);
  padding: 24px 20px;
  flex-shrink: 0;
  overflow-y: auto;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.sidebar-header h3 {
  font-family: var(--font-heading);
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 600;
}

.view-all {
  font-size: 12px;
  color: var(--teal-primary);
  text-decoration: none;
}

.view-all:hover {
  color: var(--teal-hover);
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}

.recent-item:hover {
  background: rgba(255, 255, 255, 0.04);
}

.recent-item:hover .recent-delete-btn {
  opacity: 1;
}

.recent-title {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.recent-status {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.recent-delete-btn {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
}

.recent-delete-btn:hover {
  color: var(--cinnabar);
  background: var(--cinnabar-surface);
}

.status-completed { background: var(--teal-surface); color: var(--teal-primary); }
.status-generating { background: var(--warm-gold-surface); color: var(--warm-gold); }
.status-draft { background: rgba(255, 255, 255, 0.05); color: var(--text-muted); }
.status-failed { background: var(--cinnabar-surface); color: var(--cinnabar); }

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

.sidebar-empty {
  text-align: center;
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 13px;
}

/* Main */
.home-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.hero-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  max-width: 600px;
  width: 100%;
}

.hero-title {
  display: flex;
  gap: 8px;
}

.title-char {
  font-family: var(--font-display);
  font-size: 48px;
  background: linear-gradient(135deg, var(--teal-primary), var(--warm-gold-light), var(--cinnabar));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-shadow: none;
}

.hero-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
  letter-spacing: 0.03em;
  font-weight: 300;
}

.upload-container {
  width: 100%;
  margin-top: 8px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  width: 100%;
  margin-top: 16px;
}

.feature-card {
  text-align: center;
  padding: 24px 16px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.04);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.feature-card:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(61, 184, 176, 0.15);
  transform: translateY(-3px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.2);
}

.feature-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  border-radius: 14px;
  background: var(--teal-surface);
  border: 1px solid rgba(61, 184, 176, 0.1);
  transition: all 0.3s;
}

.feature-card:hover .feature-icon-wrap {
  background: rgba(61, 184, 176, 0.12);
  border-color: rgba(61, 184, 176, 0.25);
  box-shadow: 0 0 20px rgba(61, 184, 176, 0.1);
}

.feature-svg {
  width: 28px;
  height: 28px;
  color: var(--teal-primary);
  transition: all 0.3s;
}

.feature-card:hover .feature-svg {
  color: var(--teal-hover);
}

.feature-card h4 {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 600;
  margin-bottom: 4px;
}

.feature-card p {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

@media (max-width: 768px) {
  .home-sidebar { display: none; }
  .feature-grid { grid-template-columns: 1fr; }
}
</style>
