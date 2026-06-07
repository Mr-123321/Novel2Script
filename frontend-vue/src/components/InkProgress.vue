<template>
  <div class="ink-progress">
    <!-- 墨韵牡丹绽放 - Ink peony bloom -->
    <div class="peony">
      <div class="peony-center"></div>
      <span
        v-for="i in 8"
        :key="i"
        class="peony-petal"
        :class="{ 'peony-petal--bloom': percent > (i - 1) * 12.5 }"
        :style="{
          '--petal-rotate': (i * 45) + 'deg',
          animationDelay: (i * 0.08) + 's'
        }"
      ></span>
    </div>

    <!-- 笔锋游走 - Brush stroke stages -->
    <div class="brush-strokes">
      <div
        v-for="(stroke, i) in displayStages"
        :key="i"
        class="stroke-track"
      >
        <div
          class="stroke-fill"
          :class="{ 'stroke-fill--done': stroke.done }"
          :style="{ width: stroke.percent + '%' }"
        >
          <span class="stroke-trail" v-if="stroke.percent > 0 && stroke.percent < 100"></span>
        </div>
        <span class="stroke-label">{{ stroke.label }}</span>
        <span class="stroke-check" v-if="stroke.done">✓</span>
      </div>
    </div>

    <p class="progress-message">{{ message }}</p>
    <p class="progress-percent">{{ Math.round(percent) }}%</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Stage {
  key: string
  label: string
  done: boolean
  percent: number
}

const props = defineProps<{
  percent: number
  message?: string
  stages?: Stage[]
}>()

const displayStages = computed<Stage[]>(() => {
  if (props.stages && props.stages.length > 0) return props.stages
  // Default stages derived from percent
  return [
    { key: 'parse', label: '章节解析', done: props.percent >= 14, percent: Math.min(100, Math.max(0, (props.percent / 14) * 100)) },
    { key: 'character', label: '人物抽取', done: props.percent >= 28, percent: props.percent < 14 ? 0 : Math.min(100, ((props.percent - 14) / 14) * 100) },
    { key: 'plot', label: '剧情分析', done: props.percent >= 42, percent: props.percent < 28 ? 0 : Math.min(100, ((props.percent - 28) / 14) * 100) },
    { key: 'scene', label: '场景切分', done: props.percent >= 57, percent: props.percent < 42 ? 0 : Math.min(100, ((props.percent - 42) / 15) * 100) },
    { key: 'dialogue', label: '对白生成', done: props.percent >= 71, percent: props.percent < 57 ? 0 : Math.min(100, ((props.percent - 57) / 14) * 100) },
    { key: 'action', label: '动作生成', done: props.percent >= 85, percent: props.percent < 71 ? 0 : Math.min(100, ((props.percent - 71) / 14) * 100) },
    { key: 'assembly', label: '剧本组装', done: props.percent >= 100, percent: props.percent < 85 ? 0 : Math.min(100, ((props.percent - 85) / 15) * 100) }
  ]
})
</script>

<style scoped>
.ink-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 40px;
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
  max-width: 440px;
  width: 100%;
}

/* Peony */
.peony {
  position: relative;
  width: 80px;
  height: 80px;
}

.peony-center {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 14px;
  height: 14px;
  margin: -7px 0 0 -7px;
  border-radius: 50%;
  background: var(--teal-primary);
  box-shadow: 0 0 12px var(--teal-glow);
  z-index: 2;
}

.peony-petal {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 10px;
  height: 28px;
  margin-left: -5px;
  margin-top: -28px;
  border-radius: 50%;
  background: rgba(61, 184, 176, 0.12);
  transform-origin: center bottom;
  transform: rotate(var(--petal-rotate)) scale(0);
  transition: all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.peony-petal--bloom {
  animation: petalBloom 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
  background: linear-gradient(to top, var(--teal-primary), var(--cinnabar));
}

@keyframes petalBloom {
  0% { transform: rotate(var(--petal-rotate)) scale(0); opacity: 0; }
  50% { opacity: 1; }
  100% { transform: rotate(var(--petal-rotate)) scale(1); opacity: 1; }
}

/* Brush strokes */
.brush-strokes {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.stroke-track {
  position: relative;
  height: 24px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.03);
}

.stroke-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(90deg,
    rgba(61, 184, 176, 0.2),
    rgba(61, 184, 176, 0.5),
    var(--teal-primary)
  );
  border-radius: 4px;
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  min-width: 0;
}

.stroke-fill--done {
  background: linear-gradient(90deg, var(--teal-muted), var(--teal-primary));
}

.stroke-trail {
  position: absolute;
  right: -4px;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--teal-hover);
  box-shadow: 0 0 10px var(--teal-glow);
  animation: brushTrail 0.8s ease-in-out infinite;
}

.stroke-label {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 11px;
  color: var(--text-secondary);
  z-index: 1;
  pointer-events: none;
}

.stroke-check {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  color: var(--teal-primary);
  z-index: 1;
}

/* Message */
.progress-message {
  font-family: var(--font-heading);
  font-size: 14px;
  color: var(--text-secondary);
  letter-spacing: 0.03em;
}

.progress-percent {
  font-family: var(--font-display);
  font-size: 32px;
  color: var(--teal-primary);
  text-shadow: 0 0 16px var(--teal-glow);
}
</style>
