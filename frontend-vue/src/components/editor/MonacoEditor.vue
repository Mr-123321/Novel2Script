<template>
  <div ref="editorContainer" class="monaco-wrapper"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps<{
  modelValue: string
  language?: string
  readOnly?: boolean
  theme?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const editorContainer = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

onMounted(() => {
  if (!editorContainer.value) return

  editor = monaco.editor.create(editorContainer.value, {
    value: props.modelValue,
    language: props.language ?? 'yaml',
    theme: props.theme ?? 'vs-dark',
    readOnly: props.readOnly ?? true,
    fontSize: 14,
    fontFamily: "var(--font-mono), 'JetBrains Mono', monospace",
    minimap: { enabled: false },
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    padding: { top: 16, bottom: 16 },
    automaticLayout: true,
  })

  editor.onDidChangeModelContent(() => {
    const val = editor!.getValue()
    emit('update:modelValue', val)
  })
})

onUnmounted(() => {
  editor?.dispose()
})

watch(() => props.modelValue, (newVal) => {
  if (editor && editor.getValue() !== newVal) {
    editor.setValue(newVal)
  }
})

watch(() => props.readOnly, (val) => {
  editor?.updateOptions({ readOnly: val })
})
</script>

<style scoped>
.monaco-wrapper {
  width: 100%;
  height: 100%;
  min-height: 400px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--glass-border);
}
</style>
