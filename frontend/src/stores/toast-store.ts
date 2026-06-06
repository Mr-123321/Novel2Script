import { create } from 'zustand';

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
}

interface ToastState {
  toasts: Toast[];
  addToast: (type: Toast['type'], message: string) => void;
  removeToast: (id: string) => void;
}

let toastSeq = 0;
const MAX_VISIBLE = 3;
const AUTO_DISMISS_MS = 3000;

export const useToastStore = create<ToastState>()((set, get) => ({
  toasts: [],

  addToast: (type, message) => {
    const id = `toast-${Date.now()}-${++toastSeq}`;
    const current = get().toasts;
    // Keep at most MAX_VISIBLE - 1 existing + the new one
    const trimmed = current.slice(-(MAX_VISIBLE - 1));
    set({ toasts: [...trimmed, { id, type, message }] });

    // Auto-dismiss
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, AUTO_DISMISS_MS);
  },

  removeToast: (id) => {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
  },
}));

/** Convenience helpers */
export const toast = {
  success: (msg: string) => useToastStore.getState().addToast('success', msg),
  error: (msg: string) => useToastStore.getState().addToast('error', msg),
  warning: (msg: string) => useToastStore.getState().addToast('warning', msg),
  info: (msg: string) => useToastStore.getState().addToast('info', msg),
};
