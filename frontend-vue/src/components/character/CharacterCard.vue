<template>
  <button class="char-card" @click="store.selectCharacter(character.id)">
    <!-- Header -->
    <div class="card-header">
      <div class="card-avatar">
        <span>👤</span>
      </div>
      <div class="card-names">
        <h3 class="card-name">{{ character.canonicalName }}</h3>
        <p v-if="(character.aliases?.length ?? 0) > 0" class="card-aliases">
          {{ character.aliases!.slice(0, 2).join('、') }}
          <span v-if="(character.aliases?.length ?? 0) > 2">+{{ character.aliases!.length - 2 }}</span>
        </p>
      </div>
      <span class="role-badge" :class="'role-' + character.roleType.toLowerCase()">
        {{ roleLabel(character.roleType) }}
      </span>
    </div>

    <!-- Description -->
    <p v-if="character.description" class="card-desc">{{ character.description }}</p>

    <!-- Stats -->
    <div class="card-stats">
      <span v-if="character.gender && genderInfo" class="stat" :class="genderInfo.color">
        {{ genderInfo.icon }} {{ genderInfo.label }}
      </span>
      <span class="stat"># 出场 {{ character.appearanceCount }} 次</span>
      <span v-if="(character.relationships?.length ?? 0) > 0" class="stat">
        👥 {{ character.relationships!.length }} 关系
      </span>
    </div>

    <!-- Personality traits -->
    <div v-if="(character.personality?.length ?? 0) > 0" class="card-traits">
      <span
        v-for="(trait, i) in character.personality!.slice(0, 3)"
        :key="i"
        class="trait-chip"
      >{{ trait }}</span>
      <span v-if="(character.personality?.length ?? 0) > 3" class="trait-more">
        +{{ character.personality!.length - 3 }}
      </span>
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useScriptStore } from '@/stores/script'
import { roleLabel } from '@/lib/utils'
import type { Character } from '@/types/character'

const props = defineProps<{
  character: Character
}>()

const store = useScriptStore()

const genderDisplay: Record<string, { label: string; color: string; icon: string }> = {
  MALE: { label: '男', color: 'gender-male', icon: '♂' },
  FEMALE: { label: '女', color: 'gender-female', icon: '♀' },
}

const genderInfo = computed(() => {
  if (!props.character.gender) return null
  return genderDisplay[props.character.gender] ?? null
})
</script>

<style scoped>
.char-card {
  text-align: left;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.02);
  padding: 18px;
  cursor: pointer;
  transition: all 0.3s;
  width: 100%;
}

.char-card:hover {
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  transform: translateY(-1px);
  box-shadow: var(--shadow-glass);
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.card-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(61, 184, 176, 0.2), rgba(168, 85, 247, 0.2));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.card-names {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-aliases {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  flex-shrink: 0;
  font-weight: 500;
}

.role-protagonist { background: var(--cinnabar-surface); color: var(--cinnabar); }
.role-deuteragonist { background: var(--warm-gold-surface); color: var(--warm-gold); }
.role-antagonist { background: rgba(155, 89, 182, 0.15); color: #a78bfa; }
.role-supporting { background: var(--teal-surface); color: var(--teal-primary); }
.role-minor { background: rgba(255, 255, 255, 0.05); color: var(--text-muted); }

.card-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 10px;
}

.card-stats {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: var(--text-muted);
}

.stat {
  display: flex;
  align-items: center;
  gap: 2px;
}

.gender-male { color: #60a5fa; }
.gender-female { color: #f472b6; }

.card-traits {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.trait-chip {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-muted);
}

.trait-more {
  font-size: 10px;
  color: var(--text-muted);
}
</style>
