import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Script, Scene, Dialogue, WorkflowProgress } from '@/types/script'
import type { Character } from '@/types/character'

export const useScriptStore = defineStore('script', () => {
  const script = ref<Script | null>(null)
  const progress = ref<WorkflowProgress | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const selectedSceneId = ref<number | null>(null)
  const selectedCharacterId = ref<number | null>(null)

  // Computed getters
  const selectedScene = computed(() =>
    script.value?.scenes.find((s) => s.id === selectedSceneId.value) ?? null
  )
  const selectedCharacter = computed(() =>
    script.value?.characters.find((c) => c.id === selectedCharacterId.value) ?? null
  )

  // Actions
  function setScript(newScript: Script) {
    script.value = newScript
    isLoading.value = false
    error.value = null
  }

  function clearScript() {
    script.value = null
    progress.value = null
    isLoading.value = false
    error.value = null
  }

  function setIsLoading(loading: boolean) {
    isLoading.value = loading
  }

  function updateProgress(p: WorkflowProgress) {
    progress.value = p
  }

  function updateScene(sceneId: number, updates: Partial<Scene>) {
    if (!script.value) return
    const scenes = script.value.scenes.map((s) =>
      s.id === sceneId ? { ...s, ...updates } : s
    )
    script.value = { ...script.value, scenes }
  }

  function updateDialogue(sceneId: number, sequence: number, updates: Partial<Dialogue>) {
    if (!script.value) return
    const scenes = script.value.scenes.map((scene) => {
      if (scene.id !== sceneId) return scene
      return {
        ...scene,
        dialogues: scene.dialogues.map((d) =>
          d.sequence === sequence ? { ...d, ...updates } : d
        ),
      }
    })
    script.value = { ...script.value, scenes }
  }

  function updateCharacter(characterId: number, updates: Partial<Character>) {
    if (!script.value) return
    const characters = script.value.characters.map((c) =>
      c.id === characterId ? { ...c, ...updates } : c
    )
    script.value = { ...script.value, characters }
  }

  function selectScene(sceneId: number | null) {
    selectedSceneId.value = sceneId
    selectedCharacterId.value = null
  }

  function selectCharacter(characterId: number | null) {
    selectedCharacterId.value = characterId
    selectedSceneId.value = null
  }

  function setError(e: string | null) {
    error.value = e
  }

  return {
    script,
    progress,
    isLoading,
    error,
    selectedSceneId,
    selectedCharacterId,
    selectedScene,
    selectedCharacter,
    setScript,
    clearScript,
    setIsLoading,
    updateProgress,
    updateScene,
    updateDialogue,
    updateCharacter,
    selectScene,
    selectCharacter,
    setError,
  }
})
