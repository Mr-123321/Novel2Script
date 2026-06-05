'use client';

import { useEffect, useRef } from 'react';
import type { WorkflowProgress } from '@/types/script';

interface UseSSEOptions {
  scriptId: number;
  enabled?: boolean;
  onProgress: (data: WorkflowProgress) => void;
  onComplete?: (data: { scriptId: number; status: string }) => void;
  onError?: (event: Event) => void;
}

/**
 * SSE hook for subscribing to script generation progress.
 *
 * - Uses refs for callbacks to avoid stale closures on re-renders.
 * - Cleans up EventSource on unmount, route change, or dependency change.
 * - Handles reconnection: when `scriptId` or `enabled` changes, old
 *   connection is closed and a new one is opened.
 */
export function useSSE({
  scriptId,
  enabled = true,
  onProgress,
  onComplete,
  onError,
}: UseSSEOptions) {
  const eventSourceRef = useRef<EventSource | null>(null);

  // Keep callbacks in refs so the event handlers always call the latest version
  const onProgressRef = useRef(onProgress);
  const onCompleteRef = useRef(onComplete);
  const onErrorRef = useRef(onError);
  onProgressRef.current = onProgress;
  onCompleteRef.current = onComplete;
  onErrorRef.current = onError;

  useEffect(() => {
    if (!enabled || !scriptId) return;

    // Close any previous connection first
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }

    const url = `/api/v1/scripts/${scriptId}/progress`;
    const eventSource = new EventSource(url);
    eventSourceRef.current = eventSource;

    // Handler wrappers that delegate through refs
    const handleProgress = (event: MessageEvent) => {
      try {
        const data: WorkflowProgress = JSON.parse(event.data);
        onProgressRef.current(data);
      } catch {
        // Ignore malformed events
      }
    };

    const handleComplete = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data);
        eventSource.close();
        onCompleteRef.current?.(data);
      } catch {
        // Ignore malformed events
      }
    };

    const handleError = (event: Event) => {
      // readyState 2 = CLOSED — means the connection was already closed,
      // don't double-report it
      if (eventSource.readyState === EventSource.CLOSED) return;
      eventSource.close();
      onErrorRef.current?.(event);
    };

    // Named events from the backend
    eventSource.addEventListener('progress', handleProgress);
    eventSource.addEventListener('complete', handleComplete);
    eventSource.addEventListener('error', handleError);

    // Fallback for connection-level errors (network, CORS, etc.)
    eventSource.onerror = () => {
      // If the connection closed unexpectedly, report it
      if (eventSource.readyState === EventSource.CLOSED) {
        onErrorRef.current?.(new Event('sse-connection-closed'));
      }
    };

    return () => {
      eventSource.removeEventListener('progress', handleProgress);
      eventSource.removeEventListener('complete', handleComplete);
      eventSource.removeEventListener('error', handleError);
      eventSource.close();
      eventSourceRef.current = null;
    };
  }, [scriptId, enabled]); // Re-create when scriptId or enabled changes

  return eventSourceRef;
}
