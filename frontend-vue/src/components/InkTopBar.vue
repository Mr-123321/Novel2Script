<template>
  <header class="topbar">
    <div class="topbar-inner">
      <!-- Logo -->
      <router-link to="/" class="logo-group">
        <span class="logo-icon">戏</span>
        <span class="logo-text">落墨成戏</span>
      </router-link>

      <!-- Center nav -- show when a script is active -->
      <nav class="topbar-nav" v-if="currentScript">
        <router-link :to="`/scripts/${currentScript.id}`" class="nav-link" active-class="nav-link--active">
          <span class="nav-icon">📜</span>
          剧本编辑
        </router-link>
        <router-link :to="`/scripts/${currentScript.id}/characters`" class="nav-link" active-class="nav-link--active">
          <span class="nav-icon">👤</span>
          人物管理
        </router-link>
        <router-link :to="`/scripts/${currentScript.id}/yaml`" class="nav-link" active-class="nav-link--active">
          <span class="nav-icon">📄</span>
          YAML
        </router-link>
      </nav>

      <!-- Right section -->
      <div class="topbar-right">
        <span v-if="currentScript" class="current-title">
          {{ currentScript.title }}
        </span>

        <router-link to="/scripts" class="nav-link" active-class="nav-link--active" v-if="!currentScript">
          <span class="nav-icon">📋</span>
          剧本列表
        </router-link>

        <div class="user-avatar">
          <span>墨</span>
        </div>
      </div>
    </div>
    <!-- Gradient bottom edge -->
    <div class="topbar-edge"></div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useScriptStore } from '@/stores/script'

const route = useRoute()
const store = useScriptStore()

const currentScript = computed(() => {
  const id = Number(route.params.id)
  if (!id) return null
  // Use the loaded script if IDs match, otherwise return basic info
  if (store.script && store.script.id === id) {
    return store.script
  }
  return null
})
</script>

<style scoped>
.topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: var(--topbar-height);
  background: rgba(15, 17, 21, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  z-index: 1000;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.topbar-edge {
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(61, 184, 176, 0.15) 30%,
    rgba(61, 184, 176, 0.25) 50%,
    rgba(61, 184, 176, 0.15) 70%,
    transparent
  );
}

.topbar-inner {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 20px;
  gap: 32px;
}

/* Logo */
.logo-group {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  flex-shrink: 0;
}

.logo-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--teal-primary), var(--teal-muted));
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 700;
  border-radius: 8px;
  box-shadow: 0 2px 12px var(--teal-glow);
}

.logo-text {
  font-family: var(--font-display);
  font-size: 18px;
  color: var(--text-primary);
  letter-spacing: 0.05em;
}

/* Nav */
.topbar-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 13px;
  text-decoration: none;
  transition: all 0.2s;
}

.nav-link:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.04);
}

.nav-link--active {
  color: var(--teal-primary);
  background: var(--teal-surface);
  box-shadow: inset 0 1px 0 rgba(61, 184, 176, 0.1);
}

.nav-icon {
  font-size: 14px;
}

/* Right */
.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.current-title {
  color: var(--warm-gold-light);
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 600;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-avatar {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--cinnabar), var(--cinnabar-hover));
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 700;
  border-radius: 50%;
  box-shadow: 0 2px 8px var(--cinnabar-glow);
}
</style>
