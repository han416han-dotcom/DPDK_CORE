<template>
  <div class="diagnosis-panel">
    <el-card shadow="never">
      <template #header>
        <div class="dp-header">
          <span><el-icon style="vertical-align: middle; margin-right: 6px"><WarningFilled /></el-icon>智能诊断结果</span>
          <div class="dp-header-right">
            <el-tag v-if="diag.subPatternLabel" size="small" type="warning" effect="plain">
              {{ diag.subPatternLabel }}
            </el-tag>
            <el-tag v-if="diag.isAbortChain" size="small" type="info" effect="plain">
              abort 链, 根因在 #{{ diag.abortSourceDepth }}
            </el-tag>
            <el-tag size="small" type="info" effect="plain">v2</el-tag>
          </div>
        </div>
      </template>

      <!-- Confidence Bar -->
      <div class="confidence-bar" v-if="diag.confidence">
        <span class="cb-label">可信度</span>
        <el-progress
          :percentage="diag.confidence"
          :color="confidenceColor"
          :stroke-width="8"
          style="flex:1"
        />
        <span :class="['cb-value', confidenceTextClass]">{{ diag.confidence }}%</span>
      </div>

      <el-row :gutter="16" style="margin-top: 12px">
        <!-- Crash type card -->
        <el-col :span="7">
          <div :class="['crash-type-card', crashTypeClass]">
            <div class="ct-icon">
              <el-icon :size="26"><WarningFilled /></el-icon>
            </div>
            <div class="ct-body">
              <div class="ct-label">崩溃类型</div>
              <div class="ct-value">{{ diag.crashType || '未知' }}</div>
              <div class="ct-desc">{{ diag.signal ? ('信号: ' + diag.signal) : '无信号信息' }}</div>
            </div>
          </div>
        </el-col>

        <!-- Crash function card -->
        <el-col :span="7">
          <div class="crash-type-card suspect">
            <div class="ct-icon">
              <el-icon :size="26"><Search /></el-icon>
            </div>
            <div class="ct-body">
              <div class="ct-label">
                {{ diag.isAbortChain ? 'abort 触发源 (#' + diag.abortSourceDepth + ')' : '崩溃函数' }}
              </div>
              <div class="ct-value" :title="diag.crashFunction">{{ diag.crashFunction || '???' }}</div>
              <div class="ct-desc">{{ diag.sourceLocation || '无法定位' }}</div>
            </div>
          </div>
        </el-col>

        <!-- Root cause card -->
        <el-col :span="10">
          <div class="crash-type-card action">
            <div class="ct-icon">
              <el-icon :size="26"><Tools /></el-icon>
            </div>
            <div class="ct-body">
              <div class="ct-label">根因推断</div>
              <div class="ct-value-sub">{{ diag.rootCause || '无法确定根因' }}</div>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- Key Functions -->
      <div v-if="diag.keyFunctions && diag.keyFunctions.length > 0" class="key-funcs">
        <span class="kf-label">关键函数:</span>
        <el-tag
          v-for="(fn, i) in diag.keyFunctions"
          :key="i"
          size="small"
          type="warning"
          style="margin-right: 4px"
        >{{ fn }}</el-tag>
      </div>

      <!-- Contention Threads -->
      <div v-if="diag.contentionDetected && diag.relatedThreads?.length" class="contention-box">
        <div class="contention-header">
          <el-icon style="color:#e6a23c"><WarningFilled /></el-icon>
          <span>检测到资源竞争 ({{ diag.relatedThreads.length }} 个线程)</span>
        </div>
        <div class="contention-threads">
          <div v-for="t in diag.relatedThreads" :key="t.id" class="contention-thread">
            <span class="ctn-thread-id">{{ t.name || ('Thread-' + t.id) }}</span>
            <span class="ctn-thread-func">{{ t.func }}</span>
            <el-tag size="small" type="warning" effect="plain">{{ t.note }}</el-tag>
          </div>
        </div>
      </div>

      <!-- Suggestion Steps -->
      <div class="suggestion-box" v-if="diag.suggestion">
        <div class="suggestion-header">
          <el-icon style="color:#409eff"><List /></el-icon>
          <span>排查建议</span>
        </div>
        <div class="suggestion-steps">
          <div
            v-for="(step, i) in suggestionSteps"
            :key="i"
            class="suggestion-step"
          >
            <span class="ss-num">{{ i + 1 }}</span>
            <span class="ss-text">{{ step }}</span>
          </div>
        </div>
      </div>

      <!-- Fault address detail -->
      <div v-if="diag.faultAddress && diag.faultAddress !== '0x0' && diag.faultAddress !== '0x0000000000000000'" class="fault-addr-hint">
        <el-icon style="margin-right: 4px"><InfoFilled /></el-icon>
        故障地址: <code>{{ diag.faultAddress }}</code>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: { type: Object, required: true },
})

const diag = computed(() => {
  const d = props.result?.diagnosis
  if (!d) {
    return {
      crashType: '未知',
      crashTypeId: 'UNKNOWN',
      confidence: 0,
      crashFunction: '',
      sourceLocation: '',
      signal: '',
      faultAddress: '',
      isAbortChain: false,
      abortSourceDepth: -1,
      rootCause: '无法确定崩溃类型，请查看线程分析页面获取更多信息',
      suggestion: '',
      keyFunctions: [],
      contentionDetected: false,
      relatedThreads: [],
      subPattern: '',
      subPatternLabel: '',
      abortSourceFunc: '',
    }
  }
  return {
    crashType: d.crashType || '未知',
    crashTypeId: d.crashTypeId || 'UNKNOWN',
    confidence: d.confidence || 0,
    crashFunction: d.crashFunction || '???',
    sourceLocation: d.sourceLocation || '',
    signal: d.signal || '',
    faultAddress: d.faultAddress || '',
    isAbortChain: d.isAbortChain || false,
    abortSourceDepth: d.abortSourceDepth != null ? d.abortSourceDepth : -1,
    rootCause: d.rootCause || '',
    suggestion: d.suggestion || '',
    keyFunctions: d.keyFunctions || [],
    contentionDetected: d.contentionDetected || false,
    relatedThreads: d.relatedThreads || [],
    subPattern: d.subPattern || '',
    subPatternLabel: d.subPatternLabel || '',
    abortSourceFunc: d.abortSourceFunc || '',
  }
})

const suggestionSteps = computed(() => {
  const text = diag.value.suggestion || ''
  return text.split('\n').filter(s => s.trim())
})

const crashTypeClass = computed(() => {
  const id = diag.value.crashTypeId
  if (id === 'NULL_POINTER') return 'sigsegv'
  if (id === 'MEMPOOL_CORRUPTION') return 'sigsegv'
  if (id === 'DRIVER_CONFLICT') return 'sigsegv'
  if (id === 'THREAD_CONTENTION') return 'sigabrt'
  if (id === 'USE_AFTER_FREE') return 'sigfpe'
  if (id === 'BUFFER_OVERFLOW') return 'sigabrt'
  if (id === 'ASSERTION_FAILURE') return 'sigabrt'
  if (id === 'ARITHMETIC_ERROR') return 'sigfpe'
  if (id === 'BUS_ERROR') return 'default'
  return 'default'
})

const confidenceColor = computed(() => {
  const c = diag.value.confidence
  if (c >= 80) return '#67c23a'
  if (c >= 50) return '#e6a23c'
  return '#f56c6c'
})

const confidenceTextClass = computed(() => {
  const c = diag.value.confidence
  if (c >= 80) return 'conf-high'
  if (c >= 50) return 'conf-mid'
  return 'conf-low'
})
</script>

<style scoped>
.diagnosis-panel {
  margin-bottom: 16px;
}

.dp-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.dp-header-right {
  display: flex;
  gap: 6px;
  align-items: center;
}

/* Confidence Bar */
.confidence-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}

.cb-label {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.cb-value {
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.conf-high { color: #67c23a; }
.conf-mid { color: #e6a23c; }
.conf-low { color: #f56c6c; }

/* Crash Type Cards */
.crash-type-card {
  display: flex;
  gap: 10px;
  padding: 14px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  height: 100%;
  box-sizing: border-box;
}

.ct-icon {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  flex-shrink: 0;
}

.ct-body {
  flex: 1;
  min-width: 0;
}

.ct-label {
  font-size: 11px;
  color: #909399;
  margin-bottom: 2px;
}

.ct-value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ct-value-sub {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ct-desc {
  font-size: 11px;
  color: #606266;
  line-height: 1.4;
}

/* Colors */
.sigsegv .ct-icon { background: #fef0f0; color: #f56c6c; }
.sigsegv { border-left: 3px solid #f56c6c; }

.sigabrt .ct-icon { background: #fdf6ec; color: #e6a23c; }
.sigabrt { border-left: 3px solid #e6a23c; }

.sigfpe .ct-icon { background: #f0f9eb; color: #67c23a; }
.sigfpe { border-left: 3px solid #67c23a; }

.default .ct-icon { background: #f0f5ff; color: #409eff; }
.default { border-left: 3px solid #409eff; }

.suspect { border-left: 3px solid #409eff; }
.suspect .ct-icon { background: #f0f5ff; color: #409eff; }

.action { border-left: 3px solid #e6a23c; }
.action .ct-icon { background: #fdf6ec; color: #e6a23c; }

/* Key Functions */
.key-funcs {
  margin-top: 12px;
  padding: 8px 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.kf-label {
  font-size: 12px;
  color: #909399;
  margin-right: 4px;
}

/* Contention Box */
.contention-box {
  margin-top: 12px;
  padding: 12px;
  background: #fef7e0;
  border-radius: 6px;
  border: 1px solid #f5dab1;
}

.contention-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #e6a23c;
  margin-bottom: 8px;
}

.contention-threads {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.contention-thread {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: #fff;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  font-size: 12px;
}

.ctn-thread-id {
  color: #606266;
  font-weight: 500;
}

.ctn-thread-func {
  color: #909399;
  font-family: 'Consolas', monospace;
}

/* Suggestion */
.suggestion-box {
  margin-top: 14px;
  padding: 12px;
  background: #f0f5ff;
  border-radius: 6px;
  border: 1px solid #9fceff;
}

.suggestion-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 8px;
}

.suggestion-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.suggestion-step {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.ss-num {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.ss-text {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
}

/* Fault Address */
.fault-addr-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
}

.fault-addr-hint code {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
}
</style>
