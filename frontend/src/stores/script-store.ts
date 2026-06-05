import { create } from 'zustand';
import type { Script, Scene, Dialogue, WorkflowProgress } from '@/types/script';
import type { Character } from '@/types/character';

interface ScriptState {
  script: Script | null;
  progress: WorkflowProgress | null;
  isLoading: boolean;
  error: string | null;
  selectedSceneId: number | null;
  selectedCharacterId: number | null;

  // Actions
  setScript: (script: Script) => void;
  clearScript: () => void;
  updateProgress: (progress: WorkflowProgress) => void;
  updateScene: (sceneId: number, updates: Partial<Scene>) => void;
  updateDialogue: (dialogueId: number, updates: Partial<Dialogue>) => void;
  updateCharacter: (characterId: number, updates: Partial<Character>) => void;
  selectScene: (sceneId: number | null) => void;
  selectCharacter: (characterId: number | null) => void;
  setError: (error: string | null) => void;
}

export const useScriptStore = create<ScriptState>()((set, get) => ({
  script: null,
  progress: null,
  isLoading: false,
  error: null,
  selectedSceneId: null,
  selectedCharacterId: null,

  setScript: (script) => set({ script, isLoading: false, error: null }),

  clearScript: () =>
    set({ script: null, progress: null, isLoading: false, error: null }),

  updateProgress: (progress) => set({ progress }),

  updateScene: (sceneId, updates) => {
    const script = get().script;
    if (!script) return;
    const scenes = script.scenes.map((s) =>
      s.id === sceneId ? { ...s, ...updates } : s
    );
    set({ script: { ...script, scenes } });
  },

  updateDialogue: (dialogueId, updates) => {
    const script = get().script;
    if (!script) return;
    const scenes = script.scenes.map((scene) => ({
      ...scene,
      dialogues: scene.dialogues.map((d) =>
        d.id === dialogueId ? { ...d, ...updates } : d
      ),
    }));
    set({ script: { ...script, scenes } });
  },

  updateCharacter: (characterId, updates) => {
    const script = get().script;
    if (!script) return;
    const characters = script.characters.map((c) =>
      c.id === characterId ? { ...c, ...updates } : c
    );
    set({ script: { ...script, characters } });
  },

  selectScene: (sceneId) => set({ selectedSceneId: sceneId, selectedCharacterId: null }),

  selectCharacter: (characterId) =>
    set({ selectedCharacterId: characterId, selectedSceneId: null }),

  setError: (error) => set({ error }),
}));
