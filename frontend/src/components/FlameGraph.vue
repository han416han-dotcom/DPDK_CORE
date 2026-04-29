<template>
  <div class="flame-graph" :style="{ height: graphHeight + 'px' }">
    <div class="fg-legend">
      <span class="legend-item"><span class="dot dot-crash"></span>崩溃帧</span>
      <span class="legend-item"><span class="dot dot-dpdk"></span>DPDK 函数</span>
      <span class="legend-item"><span class="dot dot-normal"></span>普通帧</span>
    </div>

    <div class="fg-scroll">
      <div
        v-for="thread in threads"
        :key="thread.id"
        :class="['fg-thread', { 'fg-thread-crash': thread.crashThread }]"
      >
        <div class="fg-thread-label" :title="thread.threadName">
          <el-tag v-if="thread.crashThread" size="small" effect="plain" type="danger">崩溃</el-tag>
          <span class="fg-thread-name">{{ thread.threadName || 'Thread-' + thread.threadId }}</span>
          <span class="fg-thread-meta">{{ thread.stackDepth }} 帧</span>
        </div>

        <div class="fg-bars">
          <div
            v-for="frame in thread.frames"
            :key="frame.index"
            :class="['fg-bar', {
              'fg-bar-crash': thread.crashThread && frame.index === 0,
              'fg-bar-dpdk': frame.isDpdkFunc,
            }]"
            :style="{
              width: barWidth + 'px',
              background: getBarColor(frame, thread),
              minWidth: frame.functionName ? Math.min(frame.functionName.length * 7 + 24, 146) + 'px' : '48px',
            }"
            @click="$emit('selectFrame', { thread, frame })"
          >
            <span class="fg-bar-text">{{ frame.functionName || '??' }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!threads || threads.length === 0" class="fg-empty">暂无可视化堆栈数据</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  threads: { type: Array, default: () => [] },
  height: { type: Number, default: 300 },
  barWidth: { type: Number, default: 120 },
})

defineEmits(['selectFrame'])

const graphHeight = computed(() => {
  const threadCount = props.threads?.length || 0
  const safeMinHeight = threadCount > 0 ? 152 : 128
  return Math.max(props.height, safeMinHeight)
})

const COLORS = {
  normal: ['#8ea7d4', '#89afb9', '#95b19e', '#bda892', '#9ea9c4', '#a89fc5'],
  dpdk: ['#c49391', '#c9a075', '#b7a475', '#8baaa3', '#9c92bc', '#b695aa'],
}

function getBarColor(frame, thread) {
  if (thread.crashThread && frame.index === 0) return '#cf8f93'
  const palette = frame.isDpdkFunc ? COLORS.dpdk : COLORS.normal
  return palette[frame.index % palette.length]
}
</script>

<style scoped>
.flame-graph {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(223, 216, 205, 0.9);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
}

.fg-legend {
  display: flex;
  gap: 18px;
  padding: 12px 16px;
  background: rgba(248, 244, 238, 0.82);
  border-bottom: 1px solid rgba(228, 221, 211, 0.88);
  font-size: 12px;
  color: var(--app-text-secondary);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 999px;
  display: inline-block;
}

.dot-crash { background: #cf8f93; }
.dot-dpdk { background: #c9a075; }
.dot-normal { background: #8ea7d4; }

.fg-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 10px 0;
  min-height: 86px;
}

.fg-thread {
  display: flex;
  align-items: center;
  margin: 4px 0;
  min-height: 36px;
}

.fg-thread-crash {
  background: rgba(252, 244, 244, 0.88);
  border-left: 3px solid rgba(207, 143, 147, 0.8);
}

.fg-thread-label {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  font-size: 12px;
  overflow: hidden;
}

.fg-thread-name {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--app-text);
}

.fg-thread-meta {
  flex-shrink: 0;
  color: var(--app-text-muted);
}

.fg-bars {
  flex: 1;
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 2px 8px 2px 0;
  overflow-x: auto;
}

.fg-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  flex-shrink: 0;
  cursor: pointer;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.16);
  transition: transform 0.18s ease, filter 0.18s ease, box-shadow 0.18s ease;
}

.fg-bar:hover {
  filter: brightness(1.02);
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(135, 141, 163, 0.16);
  z-index: 1;
}

.fg-bar-crash {
  background: #cf8f93 !important;
  box-shadow: 0 0 0 1px rgba(206, 144, 148, 0.18), 0 8px 16px rgba(207, 143, 147, 0.20);
}

.fg-bar-dpdk {
  outline: 1px solid rgba(205, 164, 117, 0.32);
  outline-offset: -1px;
}

.fg-bar-text {
  padding: 0 8px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.96);
  text-shadow: 0 1px 1px rgba(66, 75, 97, 0.24);
}

.fg-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--app-text-muted);
  font-size: 13px;
}

@media (max-width: 960px) {
  .fg-thread {
    flex-direction: column;
    align-items: stretch;
  }

  .fg-thread-label {
    width: 100%;
    padding-bottom: 6px;
  }

  .fg-bars {
    padding-left: 12px;
  }
}
</style>
