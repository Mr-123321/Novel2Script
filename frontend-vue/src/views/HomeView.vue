<template>
  <div class="home-layout">
    <!-- Sidebar: Recent Scripts -->
    <aside class="home-sidebar">
      <div class="sidebar-header">
        <h3>📜 最近剧本</h3>
        <router-link to="/scripts" class="view-all">查看全部 →</router-link>
      </div>
      <div v-if="recentScripts.length > 0" class="recent-list">
        <router-link
          v-for="script in recentScripts"
          :key="script.id"
          :to="`/scripts/${script.id}`"
          class="recent-item"
        >
          <span class="recent-title">{{ script.title }}</span>
          <span class="recent-status" :class="'status-' + script.status.toLowerCase()">
            {{ statusLabel(script.status) }}
          </span>
        </router-link>
      </div>
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
            <span class="feature-icon">🧠</span>
            <h4>智能解析</h4>
            <p>自动识别章节结构、人物关系与剧情走向</p>
          </div>
          <div class="feature-card">
            <span class="feature-icon">🎬</span>
            <h4>场景生成</h4>
            <p>AI 将小说内容切分为标准剧本场景格式</p>
          </div>
          <div class="feature-card">
            <span class="feature-icon">📄</span>
            <h4>标准导出</h4>
            <p>支持 YAML 格式导出，兼容主流剧本工具</p>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listScripts } from '@/lib/api'
import UploadZone from '@/components/novel/UploadZone.vue'
import type { Script } from '@/types/script'

const recentScripts = ref<Script[]>([])

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    COMPLETED: '已完成',
    GENERATING: '生成中',
    DRAFT: '草稿',
    FAILED: '失败',
  }
  return labels[status] ?? status
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
  text-decoration: none;
  transition: all 0.2s;
}

.recent-item:hover {
  background: rgba(255, 255, 255, 0.03);
}

.recent-title {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

.recent-status {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.status-completed { background: var(--teal-surface); color: var(--teal-primary); }
.status-generating { background: var(--warm-gold-surface); color: var(--warm-gold); }
.status-draft { background: rgba(255, 255, 255, 0.05); color: var(--text-muted); }
.status-failed { background: var(--cinnabar-surface); color: var(--cinnabar); }

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
  padding: 20px 16px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.04);
  transition: all 0.3s;
}

.feature-card:hover {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.08);
  transform: translateY(-2px);
}

.feature-icon {
  font-size: 28px;
  margin-bottom: 8px;
  display: block;
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
