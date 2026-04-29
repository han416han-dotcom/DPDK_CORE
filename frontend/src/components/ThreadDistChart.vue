<template>
  <div class="thread-dist-chart">
    <v-chart :option="option" :style="{ height: height + 'px' }" autoresize />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { BarChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'

use([GridComponent, TooltipComponent, BarChart, CanvasRenderer])

const props = defineProps({
  threads: { type: Array, default: () => [] },
  height: { type: Number, default: 300 },
})

const option = computed(() => {
  const threads = props.threads || []
  const names = threads.map(t => {
    const raw = t.threadName || 'Thread-' + t.threadId
    return raw.length > 40 ? raw.slice(0, 38) + '…' : raw
  })
  const values = threads.map(t => t.stackDepth || 0)
  const colors = threads.map(t => {
    if (t.crashThread) return '#f56c6c'
    if (t.isLcore) return '#e6a23c'
    return '#409eff'
  })

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        const idx = params[0].dataIndex
        const t = threads[idx]
        const name = t.threadName || 'Thread-' + t.threadId
        return [
          `<b>${name}</b>`,
          `线程 ID: #${t.threadId}`,
          `栈帧数: ${t.stackDepth || 0}`,
          t.crashThread ? '类型: 崩溃线程 ⚠️' : (t.isLcore ? '类型: lcore 线程' : '类型: 普通线程'),
        ].join('<br/>')
      },
    },
    grid: { left: 140, right: 40, top: 20, bottom: 20 },
    xAxis: {
      type: 'value',
      name: '帧数',
      nameTextStyle: { fontSize: 12, color: '#909399' },
      axisLabel: { fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: names,
      axisLabel: { fontSize: 11, width: 130, overflow: 'truncate' },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [{
      type: 'bar',
      data: values.map((v, i) => ({
        value: v,
        itemStyle: { color: colors[i], borderRadius: [0, 4, 4, 0] },
      })),
      barMaxWidth: 28,
      label: {
        show: true,
        position: 'right',
        formatter: p => p.value + ' 帧',
        fontSize: 11,
        color: '#606266',
      },
    }],
  }
})
</script>

<style scoped>
.thread-dist-chart {
  background: #fff;
  border-radius: 8px;
  padding: 8px;
}
</style>
