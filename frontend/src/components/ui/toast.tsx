'use client';

import { useToastStore } from '@/stores/toast-store';
import { X } from 'lucide-react';
import { useEffect, useState } from 'react';

const iconMap: Record<string, string> = {
  success: '✅',
  error: '❌',
  warning: '⚠️',
  info: 'ℹ️',
};

const styleMap: Record<string, string> = {
  success:
    'bg-emerald-500/10 border-emerald-500/30 text-emerald-200',
  error:
    'bg-red-500/10 border-red-500/30 text-red-200',
  warning:
    'bg-orange-500/10 border-orange-500/30 text-orange-200',
  info:
    'bg-blue-500/10 border-blue-500/30 text-blue-200',
};

export function ToastContainer() {
  const { toasts, removeToast } = useToastStore();
  // Track which toasts are entering (for animation)
  const [entering, setEntering] = useState<Set<string>>(new Set());

  useEffect(() => {
    // Mark new toasts as entering
    const ids = new Set(toasts.map((t) => t.id));
    setEntering((prev) => {
      const next = new Set(prev);
      ids.forEach((id) => next.add(id));
      return next;
    });
    // Clear entering after animation
    const timer = setTimeout(() => setEntering(new Set()), 400);
    return () => clearTimeout(timer);
  }, [toasts]);

  if (toasts.length === 0) return null;

  return (
    <div
      className="fixed top-4 left-1/2 -translate-x-1/2 z-[9999] flex flex-col items-center gap-2 pointer-events-none"
      aria-live="polite"
      aria-label="Notifications"
    >
      {toasts.map((t) => {
        const isEntering = entering.has(t.id);
        return (
          <div
            key={t.id}
            className={`pointer-events-auto flex items-center gap-2.5 px-4 py-2.5 rounded-lg border text-sm shadow-lg backdrop-blur-md transition-all duration-300 ${
              styleMap[t.type] ?? styleMap.info
            } ${isEntering ? 'animate-in fade-in slide-in-from-top-2' : ''}`}
            role="alert"
          >
            <span className="shrink-0 text-base leading-none">
              {iconMap[t.type] ?? iconMap.info}
            </span>
            <span className="max-w-sm leading-snug">{t.message}</span>
            <button
              onClick={() => removeToast(t.id)}
              className="ml-1 p-0.5 rounded hover:bg-white/10 transition-colors shrink-0"
              aria-label="关闭提示"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
