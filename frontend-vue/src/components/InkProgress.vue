<template>
  <div class="ink-progress">
    <!-- 墨韵牡丹 — 花瓣持续旋转绽放 -->
    <div class="peony">
      <div class="peony-center"></div>
      <span
        v-for="i in 8"
        :key="i"
        class="peony-petal peony-petal--bloom"
        :style="{
          '--petal-rotate': (i * 45) + 'deg',
          animationDelay: (i * 0.12) + 's'
        }"
      ></span>
    </div>

    <!-- 提示文案 -->
    <p class="progress-message">{{ displayMessage }}</p>

    <!-- 墨点呼吸 -->
    <div class="ink-dots">
      <span class="ink-dot" v-for="i in 3" :key="i" :style="{ animationDelay: i * 0.25 + 's' }"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  percent: number
  message?: string
}>()

const displayMessage = computed(() => {
  if (props.message) return props.message
  if (props.percent >= 100) return '生成完成 ✨'
  const p = props.percent
  if (p < 30) return '墨韵初染，解析文中意象…'
  if (p < 60) return '笔锋游走，勾勒人物脉络…'
  if (p < 85) return '落墨成章，编织场景对白…'
  return '收笔点睛，剧本即将呈现…'
})
</script>

<style scoped>
.ink-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  padding: 44px 40px;
  background: linear-gradient(135deg,
    rgba(18, 20, 24, 0.92),
    rgba(24, 28, 32, 0.88)
  );
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  box-shadow:
    0 0 60px rgba(61, 184, 176, 0.06),
    0 8px 32px rgba(0, 0, 0, 0.3);
  max-width: 400px;
  width: 100%;
}

/* ── 牡丹花 ── */
.peony {
  position: relative;
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.peony-center {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--teal-primary);
  box-shadow: 0 0 20px var(--teal-glow), 0 0 40px rgba(61, 184, 176, 0.25);
  z-index: 2;
  animation: centerPulse 2s ease-in-out infinite;
}

@keyframes centerPulse {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.2); opacity: 1; }
}

/* 花瓣 — 持续旋转 + 呼吸 */
.peony-petal {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 12px;
  height: 32px;
  margin-left: -6px;
  margin-top: -34px;
  border-radius: 50%;
  transform-origin: center bottom;
  animation: petalSway 3s ease-in-out infinite;
}

.peony-petal--bloom {
  background: linear-gradient(to top,
    rgba(61, 184, 176, 0.3),
    rgba(61, 184, 176, 0.7),
    rgba(194, 59, 34, 0.3)
  );
  transform: rotate(var(--petal-rotate)) scale(1);
}

@keyframes petalSway {
  0%, 100% {
    transform: rotate(var(--petal-rotate)) scale(0.85);
    opacity: 0.5;
  }
  25% {
    transform: rotate(var(--petal-rotate)) scale(1.05);
    opacity: 0.9;
  }
  50% {
    transform: rotate(var(--petal-rotate)) scale(1);
    opacity: 0.7;
  }
  75% {
    transform: rotate(var(--petal-rotate)) scale(0.9);
    opacity: 0.6;
  }
}


/* ── 文案 ── */
.progress-message {
  font-family: var(--font-heading);
  font-size: 14px;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
  text-align: center;
  animation: textShimmer 3s ease-in-out infinite;
}

@keyframes textShimmer {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* ── 墨点 ── */
.ink-dots {
  display: flex;
  gap: 8px;
}

.ink-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(61, 184, 176, 0.4);
  animation: inkBreathe 1.6s ease-in-out infinite;
}

.ink-dot:nth-child(1) { animation-delay: 0s; }
.ink-dot:nth-child(2) { animation-delay: 0.25s; }
.ink-dot:nth-child(3) { animation-delay: 0.5s; }

@keyframes inkBreathe {
  0%, 100% { opacity: 0.2; transform: scale(0.7); }
  50% { opacity: 0.8; transform: scale(1.3); }
}
</style>
