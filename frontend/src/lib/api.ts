import type { Novel, NovelUploadResponse } from '@/types/novel';
import type {
  Script,
  ScriptGenerateRequest,
  ScriptGenerateResponse,
  WorkflowProgress,
  WorkflowMermaidResponse,
} from '@/types/script';

const BASE_URL = '/api/v1';

/** Normalize error from various response shapes into a string message */
function normalizeError(err: unknown): string {
  if (!err) return '未知错误';
  if (typeof err === 'string') return err;
  const detail = (err as { detail?: string }).detail;
  if (detail) return detail;
  const message = (err as { message?: string }).message;
  if (message) return message;
  if (err instanceof Error) return err.message;
  return JSON.stringify(err);
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({
      status: res.status,
      title: 'Request Failed',
      detail: `HTTP ${res.status}: ${res.statusText}`,
    }));
    throw { ...body, __isApiError: true };
  }

  return res.json();
}

// ==================== Novels ====================

/** Upload a novel file */
export async function uploadNovel(file: File): Promise<NovelUploadResponse> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${BASE_URL}/novels/upload`, {
    method: 'POST',
    body: formData,
  });
  if (!res.ok) {
    const error = await res.json().catch(() => ({ detail: res.statusText }));
    throw error;
  }
  return res.json();
}

/** List all novels */
export function listNovels(): Promise<Novel[]> {
  return request<Novel[]>('/novels');
}

/** Get novel by ID */
export function getNovel(id: number): Promise<Novel> {
  return request<Novel>(`/novels/${id}`);
}

/** Delete a novel */
export function deleteNovel(id: number): Promise<void> {
  return request<void>(`/novels/${id}`, { method: 'DELETE' });
}

// ==================== Scripts ====================

/** Start script generation — returns executionId (not full Script) */
export function generateScript(
  data: ScriptGenerateRequest
): Promise<ScriptGenerateResponse> {
  return request<ScriptGenerateResponse>('/scripts/generate', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

/** Get script by ID */
export function getScript(id: number): Promise<Script> {
  return request<Script>(`/scripts/${id}`);
}

/** List all scripts */
export function listScripts(): Promise<Script[]> {
  return request<Script[]>('/scripts');
}

/** Delete a script by ID */
export function deleteScript(id: number): Promise<{ message: string }> {
  return request(`/scripts/${id}`, { method: 'DELETE' });
}

// ==================== Exports (YAML) ====================

/** Get script YAML content — backend at /api/v1/exports/{id}/yaml */
export async function getScriptYaml(
  id: number
): Promise<{ yaml: string } | { yaml: null; message: string }> {
  const res = await fetch(`${BASE_URL}/exports/${id}/yaml`);
  if (!res.ok) {
    return { yaml: null, message: 'YAML 内容尚未生成或剧本不存在' };
  }
  return res.json();
}

/** Download YAML file as blob */
export async function downloadScriptYaml(id: number): Promise<Blob> {
  const res = await fetch(`${BASE_URL}/exports/${id}/yaml/download`);
  if (!res.ok) throw new Error('Download failed');
  return res.blob();
}

// ==================== Workflow ====================

/** Get Mermaid workflow diagram */
export function getWorkflowMermaid(id: number): Promise<WorkflowMermaidResponse> {
  return request<WorkflowMermaidResponse>(`/scripts/${id}/workflow/mermaid`);
}

// ==================== Updates ====================

/** Update a scene */
export function updateScene(
  scriptId: number,
  sceneId: number,
  data: Record<string, unknown>
): Promise<{ scriptId: number; sceneId: number; updated: boolean; message: string }> {
  return request(`/scripts/${scriptId}/scenes/${sceneId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

/** Update a dialogue line */
export function updateDialogue(
  scriptId: number,
  dialogueId: number,
  data: Record<string, unknown>
): Promise<{ scriptId: number; dialogueId: number; updated: boolean; message: string }> {
  return request(`/scripts/${scriptId}/dialogues/${dialogueId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

/** Update a character */
export function updateCharacter(
  scriptId: number,
  characterId: number,
  data: Record<string, unknown>
): Promise<{ scriptId: number; characterId: number; updated: boolean; message: string }> {
  return request(`/scripts/${scriptId}/characters/${characterId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

// ==================== Paragraph CRUD ====================

/** Delete an action paragraph */
export function deleteAction(
  scriptId: number,
  sceneId: number,
  actionId: number
): Promise<{ message: string }> {
  return request(`/scripts/${scriptId}/scenes/${sceneId}/actions/${actionId}`, {
    method: 'DELETE',
  });
}

/** Delete a dialogue paragraph */
export function deleteDialogueParagraph(
  scriptId: number,
  sceneId: number,
  dialogueId: number
): Promise<{ message: string }> {
  return request(
    `/scripts/${scriptId}/scenes/${sceneId}/dialogues/${dialogueId}`,
    { method: 'DELETE' }
  );
}

/** Insert a new action paragraph */
export function addAction(
  scriptId: number,
  sceneId: number,
  data: {
    description: string;
    actionType?: string;
    sequence?: number;
    characterId?: number;
    durationMs?: number;
  }
): Promise<{ id: number; message: string }> {
  return request(`/scripts/${scriptId}/scenes/${sceneId}/actions`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

/** Insert a new dialogue paragraph */
export function addDialogue(
  scriptId: number,
  sceneId: number,
  data: {
    speaker: string;
    content: string;
    emotion?: string;
    sequence?: number;
    characterId?: number;
    parenthetical?: string;
  }
): Promise<{ id: number; message: string }> {
  return request(`/scripts/${scriptId}/scenes/${sceneId}/dialogues`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// ==================== SSE ====================

/**
 * Create SSE connection for generation progress.
 * Backend sends named events: "progress", "complete", "error"
 * Progress payload: GenerationProgress { executionId, currentStep, overallProgress, ... }
 */
export function subscribeProgress(
  scriptId: number,
  onProgress: (data: WorkflowProgress) => void,
  onComplete?: (data: { scriptId: number; status: string }) => void,
  onError?: (err: Event) => void
): EventSource {
  const eventSource = new EventSource(
    `${BASE_URL}/scripts/${scriptId}/progress`
  );

  eventSource.addEventListener('progress', (event: MessageEvent) => {
    const data: WorkflowProgress = JSON.parse(event.data);
    onProgress(data);
  });

  eventSource.addEventListener('complete', (event: MessageEvent) => {
    const data = JSON.parse(event.data);
    eventSource.close();
    onComplete?.(data);
  });

  eventSource.addEventListener('error', (event: Event) => {
    eventSource.close();
    onError?.(event);
  });

  // Fallback onerror for connection issues
  eventSource.onerror = (event) => {
    onError?.(event);
  };

  return eventSource;
}
