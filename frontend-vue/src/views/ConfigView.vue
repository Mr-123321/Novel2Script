<template>
  <div class="config-page">
    <div class="config-card">
      <router-link to="/" class="back-link">← 返回首页</router-link>
      <h1 class="config-title">🎬 剧本生成配置</h1>

      <!-- Error -->
      <div v-if="error" class="config-error">
        <span>⚠️</span> {{ error }}
      </div>

      <!-- Form -->
      <div class="config-form">
        <div class="field-group">
          <label class="field-label">最大场景数</label>
          <input
            class="field-input"
            type="number"
            v-model.number="form.maxScenes"
            min="1"
            max="100"
          />
        </div>

        <div class="field-group">
          <label class="field-label">风格</label>
          <select class="field-select" v-model="form.style">
            <option value="standard">标准</option>
            <option value="suspense">悬疑</option>
            <option value="comedy">轻喜剧</option>
            <option value="drama">正剧</option>
            <option value="epic">史诗</option>
            <option value="literary">文艺</option>
            <option value="dark">黑暗</option>
            <option value="action">动作</option>
            <option value="romance">爱情</option>
            <option value="sci-fi">科幻</option>
          </select>
        </div>

        <div class="field-group">
          <label class="field-label">焦点角色 <span class="field-hint">（逗号分隔）</span></label>
          <input
            class="field-input"
            v-model="form.focusCharacters"
            placeholder="如：林川, 李雪"
          />
        </div>

        <button
          class="btn-generate"
          :disabled="!novelId || generating"
          @click="handleGenerate"
        >
          {{ generating ? '正在创建生成任务...' : '🎬 开始生成剧本' }}
        </button>

        <p v-if="!novelId" class="form-hint">
          请先从首页上传小说文件
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { generateScript } from '@/lib/api'

const route = useRoute()
const router = useRouter()
const novelId = Number(route.query.novelId) || 0
const generating = ref(false)
const error = ref<string | null>(null)

const form = reactive({
  maxScenes: 30,
  style: 'standard',
  focusCharacters: '',
})

async function handleGenerate() {
  if (!novelId) return
  generating.value = true
  error.value = null

  try {
    const result = await generateScript({
      novelId,
      maxScenes: form.maxScenes,
      style: form.style,
      focusCharacters: form.focusCharacters || undefined,
    })
    router.push(`/scripts/${result.executionId}`)
  } catch (err: unknown) {
    const detail = (err as { detail?: string }).detail
    error.value = detail ?? (err instanceof Error ? err.message : '生成失败')
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.config-page {
  display: flex;
  justify-content: center;
  padding: 48px 24px;
}

.config-card {
  max-width: 520px;
  width: 100%;
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  padding: 32px;
  box-shadow: var(--shadow-float);
}

.back-link {
  font-size: 13px;
  color: var(--text-muted);
  text-decoration: none;
  margin-bottom: 20px;
  display: inline-block;
}

.back-link:hover {
  color: var(--teal-primary);
}

.config-title {
  font-family: var(--font-heading);
  font-size: 22px;
  color: var(--text-primary);
  margin-bottom: 24px;
}

.config-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--cinnabar-surface);
  color: var(--cinnabar);
  font-size: 13px;
  margin-bottom: 16px;
  border: 1px solid rgba(194, 59, 34, 0.15);
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.field-hint {
  font-weight: 400;
  color: var(--text-muted);
  font-size: 12px;
}

.field-input,
.field-select {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
}

.field-input:focus,
.field-select:focus {
  border-color: rgba(61, 184, 176, 0.3);
}

.field-select option {
  background: var(--ink-deep-elevated);
  color: var(--text-primary);
}

.btn-generate {
  padding: 12px 24px;
  border-radius: var(--radius-sm);
  background: var(--teal-surface);
  color: var(--teal-primary);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid rgba(61, 184, 176, 0.2);
  transition: all 0.2s;
  margin-top: 8px;
}

.btn-generate:hover:not(:disabled) {
  background: var(--teal-surface-hover);
}

.btn-generate:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.form-hint {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}
</style>
