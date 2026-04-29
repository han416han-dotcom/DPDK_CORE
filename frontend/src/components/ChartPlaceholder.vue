<template>
  <div class="chart-placeholder">
    <div class="chart-placeholder-title" v-if="title">{{ title }}</div>
    <v-chart :option="option" :style="{ height: height + 'px' }" autoresize />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { GridComponent } from 'echarts/components'
import { LineChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'

use([GridComponent, LineChart, CanvasRenderer])

const props = defineProps({
  title: { type: String, default: '' },
  height: { type: Number, default: 280 },
})

const option = computed(() => ({
  grid: { left: 0, right: 0, top: 0, bottom: 0 },
  xAxis: { show: false, type: 'category', data: [] },
  yAxis: { show: false, type: 'value' },
  series: [{
    type: 'line',
    data: [],
    smooth: true,
    lineStyle: { opacity: 0 },
    itemStyle: { opacity: 0 },
  }],
  graphic: [{
    type: 'group',
    left: 'center',
    top: 'center',
    children: [{
      type: 'text',
      style: {
        text: '📊 ' + props.title + '\n即将上线',
        textAlign: 'center',
        textVerticalAlign: 'middle',
        fontSize: 16,
        fontWeight: 500,
        fill: '#c0c4cc',
        lineWidth: 2,
      },
      left: 'center',
      top: 'center',
    }],
  }],
}))
</script>

<style scoped>
.chart-placeholder {
  background: #fff;
  border-radius: 8px;
  border: 1px dashed #dcdfe6;
  padding: 12px;
}

.chart-placeholder-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
  text-align: center;
}
</style>
