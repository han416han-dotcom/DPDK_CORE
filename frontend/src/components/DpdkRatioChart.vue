<template>
  <div class="dpdk-ratio-chart">
    <v-chart :option="option" :style="{ height: height + 'px' }" autoresize />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { PieChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'

use([TooltipComponent, LegendComponent, PieChart, CanvasRenderer])

const props = defineProps({
  threads: { type: Array, default: () => [] },
  height: { type: Number, default: 300 },
})

const option = computed(() => {
  const threads = props.threads || []
  let dpdk = 0, nonDpdk = 0
  for (const t of threads) {
    const frames = t.frames || []
    for (const f of frames) {
      if (f.isDpdkFunc) dpdk++
      else nonDpdk++
    }
  }
  const total = dpdk + nonDpdk
  const ratio = total > 0 ? ((dpdk / total) * 100).toFixed(1) : '0.0'

  return {
    tooltip: {
      trigger: 'item',
      formatter: p => {
        const pct = total > 0 ? ((p.value / total) * 100).toFixed(1) : '0.0'
        return `${p.name}<br/>${p.value} 帧 (${pct}%)`
      },
    },
    legend: {
      bottom: 0,
      textStyle: { fontSize: 12 },
    },
    graphic: [{
      type: 'group',
      left: 'center',
      top: 'center',
      children: [{
        type: 'text',
        style: {
          text: ratio + '%',
          textAlign: 'center',
          textVerticalAlign: 'middle',
          fontSize: 22,
          fontWeight: 700,
          fill: '#303133',
        },
        left: 'center',
        top: 'center',
      }, {
        type: 'text',
        style: {
          text: 'DPDK 占比',
          textAlign: 'center',
          textVerticalAlign: 'middle',
          fontSize: 12,
          fill: '#909399',
        },
        left: 'center',
        top: 24,
      }],
    }],
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      avoidLabelOverlap: true,
      padAngle: 2,
      itemStyle: { borderRadius: 4 },
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: 'bold' },
        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.15)' },
      },
      data: [
        { value: dpdk, name: 'DPDK 函数', itemStyle: { color: '#e6a23c' } },
        { value: nonDpdk, name: '其他函数', itemStyle: { color: '#c0c4cc' } },
      ],
    }],
  }
})
</script>

<style scoped>
.dpdk-ratio-chart {
  background: #fff;
  border-radius: 8px;
  padding: 8px;
}
</style>
