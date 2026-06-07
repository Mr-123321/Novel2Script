<template>
  <div class="workflow-bar" v-if="visible">
    <div class="bar-content">
      <span class="bar-icon">{{ isComplete ? '✅' : '✨' }}</span>
      <div class="bar-progress-wrap">
        <div class="bar-track">
          <div
            class="bar-fill"
            :class="{ 'bar-fill--complete': isComplete }"
            :style="{ width: progressPercent + '%' }"
          ></div>
        </div>
        <span class="bar-text">{{ statusText }}</span>
      </div>
      <span class="bar-pct">{{ Math.round(progressPercent) }}%</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  percent?: number
  message?: string
  visible?: boolean
}>()

const progressPercent = computed(() => props.percent ?? 0)
const isComplete = computed(() => progressPercent.value >= 100)
const statusText = computed(() => {
  if (props.message) return props.message
  if (isComplete.value) return '生成完成'
  return 'AI 正在生成剧本...'
})
</script>

<style scoped>
.workflow-bar {
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--glass-border);
  padding: 8px 16px;
}

.bar-content {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 56rem;
  margin: 0 auto;
}

.bar-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.bar-progress-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.bar-track {
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--teal-primary), var(--teal-hover));
  transition: width 0.5s ease-out;
}

.bar-fill--complete {
  background: linear-gradient(90deg, var(--teal-muted), var(--teal-primary));
}

.bar-text {
  font-size: 11px;
  color: var(--text-muted);
}

.bar-pct {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--teal-primary);
  font-weight: 600;
  flex-shrink: 0;
}
</style>
