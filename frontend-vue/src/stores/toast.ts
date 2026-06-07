import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Toast {
  id: string
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
}

let toastSeq = 0
const MAX_VISIBLE = 3
const AUTO_DISMISS_MS = 3000

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<Toast[]>([])

  function addToast(type: Toast['type'], message: string) {
    const id = `toast-${Date.now()}-${++toastSeq}`
    const trimmed = toasts.value.slice(-(MAX_VISIBLE - 1))
    toasts.value = [...trimmed, { id, type, message }]

    setTimeout(() => {
      toasts.value = toasts.value.filter((t) => t.id !== id)
    }, AUTO_DISMISS_MS)
  }

  function removeToast(id: string) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  return { toasts, addToast, removeToast }
})

/** Convenience helpers */
export const toast = {
  success: (msg: string) => useToastStore().addToast('success', msg),
  error: (msg: string) => useToastStore().addToast('error', msg),
  warning: (msg: string) => useToastStore().addToast('warning', msg),
  info: (msg: string) => useToastStore().addToast('info', msg),
}
