import type { Novel, NovelUploadResponse } from '@/types/novel';
import type { Script, ScriptGenerateRequest, WorkflowProgress } from '@/types/script';
import type { ApiError } from '@/types/api';

const BASE_URL = '/api/v1';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!res.ok) {
    const error: ApiError = await res.json().catch(() => ({
      status: res.status,
      title: 'Unknown Error',
      detail: res.statusText,
    }));
    throw error;
  }

  return res.json();
}

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

/** Start script generation */
export function generateScript(data: ScriptGenerateRequest): Promise<Script> {
  return request<Script>('/scripts/generate', {
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

/** Get script YAML content */
export function getScriptYaml(id: number): Promise<string> {
  return fetch(`${BASE_URL}/scripts/${id}/yaml`).then(res => res.text());
}

/** Update a scene */
export function updateScene(scriptId: number, sceneId: number, data: Record<string, unknown>): Promise<void> {
  return request<void>(`/scripts/${scriptId}/scenes/${sceneId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

/** Update a dialogue line */
export function updateDialogue(scriptId: number, dialogueId: number, data: Record<string, unknown>): Promise<void> {
  return request<void>(`/scripts/${scriptId}/dialogues/${dialogueId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

/** Update a character */
export function updateCharacter(scriptId: number, characterId: number, data: Record<string, unknown>): Promise<void> {
  return request<void>(`/scripts/${scriptId}/characters/${characterId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

/** Create SSE connection for generation progress */
export function subscribeProgress(
  scriptId: number,
  onMessage: (data: WorkflowProgress) => void,
  onError?: (err: Event) => void
): EventSource {
  const eventSource = new EventSource(`${BASE_URL}/scripts/${scriptId}/progress`);
  eventSource.onmessage = (event) => {
    const data: WorkflowProgress = JSON.parse(event.data);
    onMessage(data);
  };
  if (onError) {
    eventSource.onerror = onError;
  }
  return eventSource;
}
