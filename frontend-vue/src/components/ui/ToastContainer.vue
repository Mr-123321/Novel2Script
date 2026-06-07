<template>
  <Teleport to="body">
    <div class="toast-container" v-if="store.toasts.length > 0">
      <TransitionGroup name="toast" tag="div" class="toast-list">
        <div
          v-for="toast in store.toasts"
          :key="toast.id"
          class="toast-item"
          :class="'toast--' + toast.type"
          @click="store.removeToast(toast.id)"
        >
          <span class="toast-icon">{{ iconMap[toast.type] }}</span>
          <span class="toast-message">{{ toast.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { useToastStore } from '@/stores/toast'

const store = useToastStore()

const iconMap: Record<string, string> = {
  success: '✅',
  error: '❌',
  warning: '⚠️',
  info: 'ℹ️',
}
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 80px;
  right: 20px;
  z-index: 10000;
  pointer-events: none;
}

.toast-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--glass-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-glass);
  cursor: pointer;
  pointer-events: auto;
  min-width: 240px;
  max-width: 360px;
  transition: all 0.2s;
}

.toast-item:hover {
  border-color: var(--glass-border-hover);
  box-shadow: var(--shadow-glass-hover);
}

.toast--success {
  border-left: 3px solid var(--teal-primary);
}

.toast--error {
  border-left: 3px solid var(--cinnabar);
}

.toast--warning {
  border-left: 3px solid var(--warm-gold);
}

.toast--info {
  border-left: 3px solid var(--teal-muted);
}

.toast-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.toast-message {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.4;
}

/* TransitionGroup animations */
.toast-enter-active {
  transition: all 0.3s ease-out;
}

.toast-leave-active {
  transition: all 0.2s ease-in;
}

.toast-enter-from {
  transform: translateX(40px);
  opacity: 0;
}

.toast-leave-to {
  transform: translateX(40px);
  opacity: 0;
}

.toast-move {
  transition: transform 0.2s ease;
}
</style>
