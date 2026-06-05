import type { Character } from './character';

export type ScriptStatus = 'DRAFT' | 'GENERATING' | 'COMPLETED' | 'FAILED';

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
  progress: number;
  scenes: Scene[];
  characters: Character[];
  plotEvents: PlotEvent[];
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

export interface WorkflowProgress {
  scriptId: number;
  currentStep: WorkflowStep;
  progress: number;
  message: string;
  timestamp: string;
}

export interface ScriptGenerateRequest {
  novelId: number;
  maxScenes: number;
  style?: string;
  focusCharacters?: string[];
}
