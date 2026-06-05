'use client';

import { useEffect, useRef } from 'react';
import type { WorkflowProgress } from '@/types/script';

interface UseSSEOptions {
  scriptId: number;
  enabled?: boolean;
  onMessage: (data: WorkflowProgress) => void;
  onError?: (event: Event) => void;
  onComplete?: () => void;
}

export function useSSE({
  scriptId,
  enabled = true,
  onMessage,
  onError,
  onComplete,
}: UseSSEOptions) {
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled || !scriptId) return;

    const eventSource = new EventSource(
      `/api/v1/scripts/${scriptId}/progress`
    );

    eventSource.onmessage = (event) => {
      const data: WorkflowProgress = JSON.parse(event.data);
      onMessage(data);
      if (data.progress >= 100) {
        eventSource.close();
        onComplete?.();
      }
    };

    eventSource.onerror = (event) => {
      onError?.(event);
      eventSource.close();
    };

    eventSourceRef.current = eventSource;

    return () => {
      eventSource.close();
    };
  }, [scriptId, enabled, onMessage, onError, onComplete]);

  return eventSourceRef;
}
