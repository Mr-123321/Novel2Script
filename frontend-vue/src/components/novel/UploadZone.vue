<template>
  <div class="upload-zone-wrapper">
    <!-- 墨砚上传区域 -->
    <div
      class="inkstone-zone"
      :class="{ 'inkstone-zone--drag': isDragOver }"
      @dragover.prevent="onDragOver"
      @dragleave.prevent="onDragLeave"
      @drop.prevent="onDrop"
      @click="openFileDialog"
    >
      <input
        ref="fileInput"
        type="file"
        accept=".txt,.md"
        class="file-input-hidden"
        @change="onFileSelect"
      />

      <!-- Uploading state -->
      <div v-if="uploading" class="upload-state">
        <div class="upload-spinner-container">
          <div class="upload-spinner"></div>
          <div class="spinner-ring"></div>
        </div>
        <p class="upload-title">正在上传解析...</p>
        <p class="upload-subtitle">AI 正在分析小说结构与人物关系</p>
      </div>

      <!-- Ready state -->
      <div v-else class="ready-state">
        <!-- Inkstone circle -->
        <div class="inkstone-circle" :class="{ 'inkstone-circle--drag': isDragOver }">
          <span class="inkstone-icon">{{ isDragOver ? '⬆️' : '📤' }}</span>
        </div>
        <p class="upload-title">{{ isDragOver ? '松开以放置文件' : '拖拽小说文件到此处，或点击上传' }}</p>
        <p class="upload-subtitle">支持 .txt / .md 格式 · 最大 50 MB</p>
        <div class="format-badges">
          <span class="format-badge">.txt</span>
          <span class="format-badge">.md</span>
        </div>
      </div>
    </div>

    <!-- Error display -->
    <div v-if="error" class="upload-error">
      <span class="error-icon">⚠️</span>
      {{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { uploadNovel } from '@/lib/api'

const router = useRouter()
const fileInput = ref<HTMLInputElement | null>(null)
const isDragOver = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)

function onDragOver() {
  isDragOver.value = true
}

function onDragLeave() {
  isDragOver.value = false
}

async function onDrop(e: DragEvent) {
  isDragOver.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) await processFile(file)
}

function openFileDialog() {
  fileInput.value?.click()
}

function onFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) processFile(file)
  // Reset so the same file can be re-selected
  input.value = ''
}

async function processFile(file: File) {
  // Validate
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (ext !== 'txt' && ext !== 'md') {
    error.value = '仅支持 .txt 和 .md 格式文件'
    return
  }
  if (file.size > 50 * 1024 * 1024) {
    error.value = '文件大小不能超过 50 MB'
    return
  }

  error.value = null
  uploading.value = true

  try {
    const data = await uploadNovel(file)
    router.push(`/scripts/new?novelId=${data.novelId}`)
  } catch (err: unknown) {
    const detail = (err as { detail?: string }).detail
    const message = err instanceof Error ? err.message : '上传失败，请检查后端服务'
    error.value = detail ?? message
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-zone-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inkstone-zone {
  position: relative;
  border-radius: var(--radius-xl);
  padding: 48px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px dashed rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.01);
}

.inkstone-zone:hover {
  border-color: rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.02);
}

.inkstone-zone--drag {
  border-color: var(--teal-primary) !important;
  background: var(--teal-surface) !important;
  transform: scale(1.02);
  box-shadow: 0 0 40px rgba(45, 212, 191, 0.1);
}

.file-input-hidden {
  display: none;
}

/* Uploading */
.upload-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.upload-spinner-container {
  position: relative;
  width: 64px;
  height: 64px;
}

.upload-spinner {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: var(--teal-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(61, 184, 176, 0.1);
  animation: breatheGlow 2s ease-in-out infinite;
}

.spinner-ring {
  position: absolute;
  inset: -3px;
  border-radius: 19px;
  border: 2px solid rgba(61, 184, 176, 0.2);
  animation: glowPulse 1.5s ease-in-out infinite;
}

@keyframes glowPulse {
  0%, 100% { opacity: 0.3; transform: scale(1); }
  50% { opacity: 0.8; transform: scale(1.05); }
}

/* Ready */
.ready-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.inkstone-circle {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
  transition: all 0.3s;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.inkstone-circle--drag {
  background: var(--teal-surface);
  border-color: rgba(61, 184, 176, 0.3);
  transform: scale(1.1);
}

.inkstone-icon {
  font-size: 28px;
}

.upload-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}

.upload-subtitle {
  font-size: 13px;
  color: var(--text-muted);
}

.format-badges {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.format-badge {
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

/* Error */
.upload-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 13px;
  color: var(--cinnabar);
  background: var(--cinnabar-surface);
  border: 1px solid rgba(194, 59, 34, 0.15);
}

.error-icon {
  flex-shrink: 0;
}
</style>
