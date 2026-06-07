import { ref, onMounted, onUnmounted, watch, type Ref } from 'vue'
import type { WorkflowProgress } from '@/types/script'
import { subscribeProgress } from '@/lib/api'

interface UseSseOptions {
  scriptId: Ref<number | null> | (() => number | null)
  enabled: Ref<boolean> | (() => boolean)
  onProgress: (data: WorkflowProgress) => void
  onComplete?: (data: { scriptId: number; status: string }) => void
  onError?: (event: Event) => void
}

export function useSse(options: UseSseOptions) {
  const eventSource = ref<EventSource | null>(null)

  function resolveScriptId(): number | null {
    if (typeof options.scriptId === 'function') return options.scriptId()
    return options.scriptId.value
  }

  function resolveEnabled(): boolean {
    if (typeof options.enabled === 'function') return options.enabled()
    return options.enabled.value
  }

  function connect() {
    const id = resolveScriptId()
    if (!id || !resolveEnabled()) return
    disconnect()

    eventSource.value = subscribeProgress(
      id,
      options.onProgress,
      (data) => {
        options.onComplete?.(data)
      },
      (event) => {
        options.onError?.(event)
      }
    )
  }

  function disconnect() {
    if (eventSource.value) {
      eventSource.value.close()
      eventSource.value = null
    }
  }

  // Watch scriptId changes
  if ('value' in options.scriptId) {
    watch(() => (options.scriptId as Ref<number | null>).value, () => {
      connect()
    })
  }

  // Watch enabled: connect when enabled becomes true, disconnect when false
  if ('value' in options.enabled) {
    watch(() => (options.enabled as Ref<boolean>).value, (val) => {
      if (val) {
        connect()
      } else {
        disconnect()
      }
    })
  }

  // Try connecting immediately on mount (if both id and enabled are ready)
  onMounted(() => {
    connect()
  })

  onUnmounted(() => {
    disconnect()
  })

  return { eventSource, connect, disconnect }
}
