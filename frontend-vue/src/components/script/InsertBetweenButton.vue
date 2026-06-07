<template>
  <div class="insert-between group">
    <div class="insert-line"></div>
    <button
      class="insert-btn"
      :class="{ 'insert-btn--active': isActive }"
      @click.stop="$emit('toggle')"
      :title="label"
    >
      <span class="plus-icon">+</span>
      <span class="insert-label">在此之后插入段落</span>
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  isActive: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<style scoped>
.insert-between {
  position: relative;
  height: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.insert-line {
  position: absolute;
  inset-inline: 0;
  top: 50%;
  border-top: 1px solid transparent;
  transition: border-color 0.2s;
}

.group:hover .insert-line {
  border-color: rgba(61, 184, 176, 0.1);
}

.insert-btn {
  position: relative;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 20px;
  font-size: 10px;
  font-weight: 500;
  color: rgba(61, 184, 176, 0.6);
  background: rgba(61, 184, 176, 0.06);
  opacity: 0;
  cursor: pointer;
  transition: all 0.15s;
}

.group:hover .insert-btn {
  opacity: 1;
}

.insert-btn:hover {
  color: var(--teal-primary);
  background: var(--teal-surface);
}

.insert-btn--active {
  opacity: 1 !important;
  color: var(--teal-primary);
  background: var(--teal-surface);
}

.plus-icon {
  font-size: 12px;
  font-weight: 700;
}

.insert-label {
  display: none;
}

.group:hover .insert-label {
  display: inline;
}
</style>
