import type { Character } from './character';

export type ScriptStatus = 'DRAFT' | 'GENERATING' | 'COMPLETED' | 'COMPLETED_WITH_WARNINGS' | 'FAILED';

export type WorkflowStep =
  | 'CHAPTER_PARSE'
  | 'CHARACTER_EXTRACT'
  | 'CHARACTER_RESOLVE'
  | 'PLOT_EXTRACT'
  | 'SCENE_SEGMENT'
  | 'DIALOGUE_GENERATE'
  | 'ACTION_GENERATE'
  | 'SCRIPT_COMPOSE'
  | 'YAML_EXPORT'
  | 'STORYBOARD_GENERATE';

export interface PlotInsertion {
  id: number;
  scriptId: number;
  text: string;
  position: number; // 0 = before first scene, N = after scene N
  insertedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface Script {
  id: number;
  novelId: number;
  title: string;
  version: number;
  sceneCount: number;
  characterCount: number;
  dialogueCount: number;
  yamlContent?: string;
  status: ScriptStatus;
  progress: number; // 0.00 - 100.00
  scenes: Scene[];
  characters: Character[];
  plotEvents: PlotEvent[];
  plotInsertions: PlotInsertion[];
  workflowState?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface Scene {
  id: number;
  scriptId: number;
  sceneNumber: number;
  title?: string;
  location: string;
  timeOfDay: string;
  interior: boolean;
  summary?: string;
  mood?: string;
  sourceReason?: string;
  sceneHeading?: string; // generated: "INT. 教室 - MORNING"
  chapterIds?: number[];
  characterIds?: number[];
  dialogues: Dialogue[];
  actions: Action[];
}

export interface Dialogue {
  id: number;
  sceneId: number;
  characterId: number;
  sequence: number;
  speaker: string;
  emotion?: string;
  content: string;
  parenthetical?: string;
  replyTo?: number;
}

export interface Action {
  id: number;
  sceneId: number;
  characterId?: number;
  sequence: number;
  actionType: string;
  description: string;
  durationMs?: number;
}

export interface PlotEvent {
  id: number;
  scriptId: number;
  eventOrder: number;
  title: string;
  description?: string;
  location?: string;
  timePoint?: string;
  conflictType?: string;
  chapterIds: number[];
  characterIds: number[];
  importance: number;
}

/** Backend GenerationProgress DTO — SSE event payload */
export interface WorkflowProgress {
  executionId: string; // script ID as string
  currentStep: WorkflowStep;
  overallProgress: number; // 0.00 - 100.00
  status: string;
  startedAt: string;
  estimatedCompletion: string;
  message: string;
}

/** Backend ScriptGenerateRequest — focusCharacters is comma-separated string */
export interface ScriptGenerateRequest {
  novelId: number;
  maxScenes: number;
  style?: string;
  focusCharacters?: string; // comma-separated, e.g. "林川, 李雪"
}

/** Response from POST /api/v1/scripts/generate */
export interface ScriptGenerateResponse {
  executionId: string;
  status: string;
  message: string;
}

/** Response from GET /api/v1/scripts/{id}/workflow/mermaid */
export interface WorkflowMermaidResponse {
  scriptId: string;
  mermaid: string;
}
