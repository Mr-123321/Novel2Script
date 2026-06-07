<template>
  <div class="app-shell">
    <InkTopBar />
    <div class="app-body">
      <transition name="fade" mode="out-in">
        <router-view />
      </transition>
    </div>
    <!-- 花瓣庆成 - petal celebration on completion -->
    <div v-if="showPetals" class="petal-container" aria-hidden="true">
      <span
        v-for="i in 16"
        :key="i"
        class="petal"
        :style="{
          left: randomPct(i, 0) + '%',
          animationDelay: randomPct(i, 1) + 's',
          animationDuration: (2.5 + randomPct(i, 2) * 3.5) + 's',
          fontSize: (10 + randomPct(i, 3) * 18) + 'px',
          opacity: 0.5 + randomPct(i, 4) * 0.5
        }"
      >
        {{ i % 3 === 0 ? '🌸' : i % 3 === 1 ? '🏵️' : '✿' }}
      </span>
    </div>
    <!-- Toast notifications -->
    <ToastContainer />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import InkTopBar from '@/components/InkTopBar.vue'
import ToastContainer from '@/components/ui/ToastContainer.vue'

const showPetals = ref(false)

// Deterministic pseudo-random based on index+seed
function randomPct(i: number, seed: number): number {
  const x = Math.sin((i + 1) * (seed + 1) * 127.1) * 43758.5453
  return x - Math.floor(x)
}

// Expose petal trigger for celebration effect
declare global {
  interface Window {
    triggerPetals?: () => void
  }
}

if (typeof window !== 'undefined') {
  window.triggerPetals = () => {
    showPetals.value = true
    setTimeout(() => { showPetals.value = false }, 4500)
  }
}
</script>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--ink-deep);
}

.app-body {
  flex: 1;
  padding-top: var(--topbar-height);
}

.petal-container {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 9999;
  overflow: hidden;
}

.petal {
  position: absolute;
  top: -24px;
  animation: petalFall linear forwards;
}
</style>
