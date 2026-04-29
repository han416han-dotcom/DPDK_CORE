<template>
  <div class="task-detail-page">
    <el-button text @click="$router.push('/tasks')" style="margin-bottom: 12px">
      <el-icon><ArrowLeft /></el-icon>
      返回列表
    </el-button>

    <!-- Loading -->
    <div v-if="loading" style="text-align: center; padding: 80px">
      <el-icon class="is-loading" :size="36"><Loading /></el-icon>
      <p style="margin-top: 12px; color: #909399">加载中...</p>
    </div>

    <!-- Error -->
    <div v-else-if="error" style="text-align: center; padding: 60px">
      <el-result icon="error" title="加载失败" :sub-title="error" />
    </div>

    <template v-else-if="result">
      <!-- Task Header Card -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <div class="task-header">
          <div class="task-header-left">
            <h2 class="task-name">{{ result.taskName }}</h2>
            <div class="task-badges">
              <el-tag v-if="result.status === 'COMPLETED'" type="success" effect="dark">解析完成</el-tag>
              <el-tag v-else-if="result.status === 'FAILED'" type="danger" effect="dark">解析失败</el-tag>
              <el-tag v-else type="warning" effect="dark">{{ result.status }}</el-tag>
              <el-tag v-if="result.crashSignal" type="danger">
                崩溃: {{ result.crashSignal }}
              </el-tag>
              <el-tag v-if="result.faultAddress" type="info">
                <code>{{ result.faultAddress }}</code>
              </el-tag>
            </div>
          </div>
          <div class="task-header-right">
            <div class="task-meta-item">
              <span class="meta-label">任务 ID</span>
              <span class="meta-value">#{{ result.taskId }}</span>
            </div>
            <div class="task-meta-item">
              <span class="meta-label">线程数</span>
              <span class="meta-value">{{ result.totalThreads || 0 }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- Tabs -->
      <el-tabs v-model="activeTab" type="border-card">
        <!-- Tab 1: Overview -->
        <el-tab-pane label="概览" name="overview">
          <div class="overview-tab">
            <!-- Stats Row -->
            <el-row :gutter="16" style="margin-bottom: 16px">
              <el-col :span="6">
                <StatsCard icon="Monitor" label="线程总数" :value="computedStats.totalThreads" :subtitle="computedStats.lcoreThreads + ' 个 lcore'" color="#1a6eff" />
              </el-col>
              <el-col :span="6">
                <StatsCard icon="List" label="栈帧总数" :value="computedStats.totalFrames" :subtitle="'平均 ' + computedStats.avgFramesPerThread + ' 帧/线程'" color="#67c23a" />
              </el-col>
              <el-col :span="6">
                <StatsCard icon="Cpu" label="DPDK 占比" :value="computedStats.dpdkRatio" subtitle="DPDK 函数帧占比" color="#e6a23c" />
              </el-col>
              <el-col :span="6">
                <StatsCard icon="WarningFilled" label="低可信度帧" :value="computedStats.lowConfidenceFrames" subtitle="置信度 < 30%" color="#f56c6c" />
              </el-col>
            </el-row>

            <!-- Crash Summary -->
            <el-card shadow="never" style="margin-bottom: 16px">
              <template #header>
                <span>崩溃摘要</span>
              </template>
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="崩溃信号">
                  <el-tag type="danger" effect="dark">{{ result.crashSignal || '未知' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="故障地址">
                  <code>{{ result.faultAddress || '未知' }}</code>
                </el-descriptions-item>
                <el-descriptions-item label="崩溃线程">
                  <el-tag v-if="crashThreadName" type="danger">{{ crashThreadName }}</el-tag>
                  <span v-else class="text-muted">未知</span>
                </el-descriptions-item>
                <el-descriptions-item label="lcore 线程数" :span="2">
                  {{ computedStats.lcoreThreads }} / {{ computedStats.totalThreads }}
                </el-descriptions-item>
                <el-descriptions-item label="DPDK 函数帧">
                  {{ computedStats.dpdkFrames }} / {{ computedStats.totalFrames }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>

            <!-- AI Diagnosis -->
            <DiagnosisPanel :result="result" style="margin-bottom: 16px" />

            <!-- Flame Graph -->
            <el-card shadow="never">
              <template #header>
                <span>调用栈火焰图</span>
                <span style="font-size:12px;color:#909399;margin-left:8px">点击帧可跳转到线程分析</span>
              </template>
              <FlameGraph
                :threads="result.threads"
                :height="Math.min(result.threads?.length * 36 + 40, 400)"
                :bar-width="140"
                @select-frame="onSelectFrame"
              />
            </el-card>
          </div>
        </el-tab-pane>

        <!-- Tab 2: Thread Analysis (existing view) -->
        <el-tab-pane label="线程分析" name="threads">
          <el-row :gutter="16">
            <!-- Left: Thread List -->
            <el-col :span="7">
              <el-card shadow="never">
                <template #header>
                  <span>线程列表 ({{ result.threads?.length || 0 }})</span>
                </template>
                <div class="thread-list">
                  <div
                    v-for="thread in result.threads"
                    :key="thread.id"
                    :class="['thread-item', { active: selectedThread?.id === thread.id, crash: thread.crashThread }]"
                    @click="selectThread(thread)"
                  >
                    <div class="thread-name">
                      <el-tag v-if="thread.crashThread" type="danger" size="small" effect="dark">崩溃</el-tag>
                      <el-tag v-if="thread.isLcore" type="warning" size="small" effect="plain">lcore</el-tag>
                      {{ thread.threadName || 'Thread-' + thread.threadId }}
                    </div>
                    <div class="thread-meta">
                      <span>#{{ thread.threadId }}</span>
                      <span>{{ thread.stackDepth }} 帧</span>
                    </div>
                  </div>
                </div>
              </el-card>
            </el-col>

            <!-- Right: Stack Frames -->
            <el-col :span="17">
              <el-card shadow="never">
                <template #header>
                  <span v-if="selectedThread">
                    调用堆栈 - {{ selectedThread.threadName || 'Thread-' + selectedThread.threadId }}
                    <el-tag v-if="selectedThread.crashThread" type="danger" size="small" style="margin-left: 8px">崩溃线程</el-tag>
                  </span>
                  <span v-else>选择左侧线程查看调用堆栈</span>
                </template>

                <div v-if="selectedThread" class="stack-frames">
                  <div
                    v-for="frame in selectedThread.frames"
                    :key="frame.index"
                    :class="['frame-item', { 'frame-crash': selectedThread.crashThread && frame.index === 0 }]"
                    @click="toggleFrame(frame)"
                  >
                    <div class="frame-header">
                      <span class="frame-index">#{{ frame.index }}</span>
                      <span :class="['func-name', { 'dpdk-func': frame.isDpdkFunc }]">
                        {{ frame.functionName || '??' }}
                      </span>
                      <span class="frame-addr" v-if="frame.address">{{ frame.address }}</span>
                      <el-tag v-if="frame.isDpdkFunc" size="small" type="warning" style="margin-left: 4px">DPDK</el-tag>
                      <el-tag v-if="frame.confidence != null && frame.confidence < 30" size="small" type="info" style="margin-left: 4px">低可信度</el-tag>
                    </div>
                    <div class="frame-source" v-if="frame.sourceFile">
                      {{ frame.sourceFile }}<span v-if="frame.sourceLine">:{{ frame.sourceLine }}</span>
                    </div>

                    <!-- Expanded detail -->
                    <div v-if="expandedFrame?.index === frame.index" class="frame-detail">
                      <el-descriptions :column="2" border size="small">
                        <el-descriptions-item label="地址" v-if="frame.address">{{ frame.address }}</el-descriptions-item>
                        <el-descriptions-item label="函数" v-if="frame.functionName">{{ frame.functionName }}</el-descriptions-item>
                        <el-descriptions-item label="源文件" v-if="frame.sourceFile">{{ frame.sourceFile }}</el-descriptions-item>
                        <el-descriptions-item label="行号" v-if="frame.sourceLine != null">{{ frame.sourceLine }}</el-descriptions-item>
                        <el-descriptions-item label="偏移" v-if="frame.offsetInFunc">{{ frame.offsetInFunc }}</el-descriptions-item>
                        <el-descriptions-item label="可信度">{{ frame.confidence != null ? frame.confidence + '%' : '0%' }}</el-descriptions-item>
                        <el-descriptions-item label="参数" v-if="frame.args" :span="2">
                          <pre class="frame-args">{{ formatArgs(frame.args) }}</pre>
                        </el-descriptions-item>
                      </el-descriptions>
                    </div>
                  </div>
                </div>

                <el-empty v-else description="请从左侧选择一个线程" />
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- Tab 3: Logs -->
        <el-tab-pane label="解析日志" name="logs">
          <el-card shadow="never">
            <template #header>
              <div class="logs-header">
                <span>解析日志 ({{ result.logs?.length || 0 }})</span>
                <div class="logs-filters">
                  <el-check-tag :checked="showInfo" @change="showInfo = !showInfo" style="margin-right: 6px">INFO</el-check-tag>
                  <el-check-tag :checked="showWarn" @change="showWarn = !showWarn" style="margin-right: 6px" type="warning">WARN</el-check-tag>
                  <el-check-tag :checked="showError" @change="showError = !showError" type="danger">ERROR</el-check-tag>
                </div>
              </div>
            </template>
            <div class="parse-logs" ref="logContainer">
              <div
                v-for="(log, i) in filteredLogs"
                :key="log.id || i"
                :class="'log-line log-' + (log.logLevel || '').toLowerCase()"
              >
                <span class="log-line-num">{{ i + 1 }}</span>
                <span class="log-level">[{{ log.logLevel }}]</span>
                <span class="log-stage">[{{ log.stage }}]</span>
                <span class="log-msg">{{ log.message }}</span>
              </div>
              <div v-if="filteredLogs.length === 0" class="log-empty">当前筛选条件下无匹配日志</div>
            </div>
          </el-card>
        </el-tab-pane>

        <!-- Tab 4: Visualization -->
        <el-tab-pane label="可视化" name="visualization">
          <el-card shadow="never" style="margin-bottom: 16px">
            <template #header>
              <span>调用栈全景图</span>
            </template>
            <FlameGraph
              :threads="result.threads"
              :height="Math.min(result.threads?.length * 40 + 40, 600)"
              :bar-width="160"
              @select-frame="onSelectFrame"
            />
          </el-card>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-card shadow="never">
                <template #header><span>线程分布</span></template>
                <ThreadDistChart :threads="result.threads" :height="300" />
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never">
                <template #header><span>DPDK 函数占比</span></template>
                <DpdkRatioChart :threads="result.threads" :height="300" />
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTaskResult } from '../api'
import { ElMessage } from 'element-plus'
import StatsCard from '../components/StatsCard.vue'
import FlameGraph from '../components/FlameGraph.vue'
import DiagnosisPanel from '../components/DiagnosisPanel.vue'
import ThreadDistChart from '../components/ThreadDistChart.vue'
import DpdkRatioChart from '../components/DpdkRatioChart.vue'

const route = useRoute()
const result = ref(null)
const loading = ref(true)
const error = ref(null)
const activeTab = ref('overview')
const selectedThread = ref(null)
const expandedFrame = ref(null)
const showInfo = ref(true)
const showWarn = ref(true)
const showError = ref(true)

// Computed: crash thread name
const crashThreadName = computed(() => {
  const t = result.value?.threads?.find(t => t.crashThread)
  return t?.threadName || t?.threadId || null
})

// Computed: aggregated stats from thread/frame data
const computedStats = computed(() => {
  const threads = result.value?.threads || []
  const totalThreads = threads.length
  const lcoreThreads = threads.filter(t => t.isLcore).length
  let totalFrames = 0
  let dpdkFrames = 0
  let lowConfidenceFrames = 0

  for (const t of threads) {
    totalFrames += t.stackDepth || 0
    if (t.frames) {
      for (const f of t.frames) {
        if (f.isDpdkFunc) dpdkFrames++
        if (f.confidence != null && f.confidence < 30) lowConfidenceFrames++
      }
    }
  }

  const dpdkRatio = totalFrames > 0
    ? ((dpdkFrames / totalFrames) * 100).toFixed(1) + '%'
    : '0.0%'

  const avgFramesPerThread = totalThreads > 0
    ? (totalFrames / totalThreads).toFixed(1)
    : '0'

  return {
    totalThreads,
    lcoreThreads,
    totalFrames,
    dpdkFrames,
    dpdkRatio,
    lowConfidenceFrames,
    avgFramesPerThread,
  }
})

// Filtered logs
const filteredLogs = computed(() => {
  const logs = result.value?.logs || []
  return logs.filter(log => {
    if (log.logLevel === 'INFO' && !showInfo.value) return false
    if (log.logLevel === 'WARN' && !showWarn.value) return false
    if (log.logLevel === 'ERROR' && !showError.value) return false
    if (log.stage === 'CRASH_DIAGNOSE') return false
    return true
  })
})

onMounted(async () => {
  try {
    const res = await getTaskResult(route.params.id)
    result.value = res.data
    // Default: select crash thread
    const crash = result.value.threads?.find(t => t.crashThread)
    if (crash) {
      selectThread(crash)
    } else if (result.value.threads?.length) {
      selectThread(result.value.threads[0])
    }
  } catch (err) {
    error.value = err.response?.data?.error || err.message
  } finally {
    loading.value = false
  }
})

function selectThread(thread) {
  selectedThread.value = thread
  expandedFrame.value = null
}

function toggleFrame(frame) {
  expandedFrame.value = expandedFrame.value?.index === frame.index ? null : frame
}

function onSelectFrame({ thread, frame }) {
  selectThread(thread)
  expandedFrame.value = frame
  activeTab.value = 'threads'
}

function formatArgs(args) {
  if (!args) return '无'
  try {
    const parsed = JSON.parse(args)
    return Array.isArray(parsed) ? parsed.join('\n') : parsed
  } catch {
    return args
  }
}
</script>

<style scoped>
.task-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}

/* Task Header */
.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.task-header-left {
  flex: 1;
}

.task-name {
  margin: 0 0 10px 0;
  font-size: 18px;
  font-weight: 600;
}

.task-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.task-badges code {
  background: transparent;
  font-size: 12px;
}

.task-header-right {
  display: flex;
  gap: 20px;
  flex-shrink: 0;
}

.task-meta-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.meta-label {
  font-size: 12px;
  color: #909399;
}

.meta-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* Overview Tab */
.overview-tab {
  min-height: 300px;
}

/* Thread List (from threads tab) */
.thread-list {
  max-height: 500px;
  overflow-y: auto;
}

.thread-item {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: background 0.2s;
}

.thread-item:hover { background: #f5f7fa; }
.thread-item.active { background: #ecf5ff; }
.thread-item.crash { border-left: 3px solid #f56c6c; }

.thread-name {
  font-size: 13px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.thread-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  display: flex;
  gap: 12px;
}

/* Stack Frames */
.stack-frames {
  max-height: 600px;
  overflow-y: auto;
}

.frame-item {
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: background 0.2s;
}

.frame-item:hover { background: #f5f7fa; }
.frame-item.frame-crash {
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
}

.frame-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.frame-index {
  font-weight: bold;
  color: #909399;
  min-width: 24px;
  font-family: monospace;
}

.func-name {
  font-family: 'Consolas', 'Courier New', monospace;
  font-weight: 500;
}

.func-name.dpdk-func {
  color: #e6a23c;
}

.frame-addr {
  color: #909399;
  font-size: 12px;
  font-family: monospace;
}

.frame-source {
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
  margin-left: 32px;
}

.frame-detail {
  margin-top: 8px;
  padding: 8px;
  background: #fafafa;
  border-radius: 4px;
}

.frame-args {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

/* Logs Tab */
.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logs-filters {
  display: flex;
}

.parse-logs {
  max-height: 500px;
  overflow-y: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 8px 0;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Courier New', monospace;
}

.log-line {
  line-height: 1.8;
  padding: 0 12px;
  display: flex;
  gap: 8px;
}

.log-line:hover {
  background: rgba(255, 255, 255, 0.05);
}

.log-line-num {
  color: #555;
  min-width: 28px;
  text-align: right;
  user-select: none;
}

.log-level {
  min-width: 50px;
  font-weight: bold;
}

.log-stage {
  color: #569cd6;
  min-width: 100px;
}

.log-error { color: #f44747; }
.log-warn { color: #dcdcaa; }
.log-info { color: #d4d4d4; }

.log-empty {
  padding: 24px;
  text-align: center;
  color: #555;
}

.text-muted {
  color: #909399;
}
</style>
