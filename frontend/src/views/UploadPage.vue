<template>
  <div class="upload-page">
    <el-card shadow="never" class="scan-card">
      <template #header>
        <div class="scan-header">
          <div>
            <div class="card-title">固定目录自动扫描</div>
            <div class="card-subtitle">扫描后端配置的固定目录中的 core 文件，并自动匹配 ELF。</div>
          </div>
          <div class="scan-actions">
            <el-switch
              v-model="autoRefresh"
              size="small"
              inline-prompt
              active-text="轮询"
              inactive-text="手动"
            />
            <el-button :loading="scanLoading" @click="refreshScan">
              <el-icon><Refresh /></el-icon>
              刷新扫描
            </el-button>
          </div>
        </div>
      </template>

      <div class="scan-summary">
        <div class="scan-stat">
          <span class="summary-label">扫描根目录</span>
          <span class="summary-value-strong">{{ scanResult?.scanRoot || '-' }}</span>
        </div>
        <div class="scan-stat">
          <span class="summary-label">候选文件</span>
          <span class="summary-value-strong">{{ scanResult?.totalSources ?? 0 }}</span>
        </div>
        <div class="scan-stat">
          <span class="summary-label">已自动匹配 ELF</span>
          <span class="summary-value-strong">{{ scanResult?.matchedSources ?? 0 }}</span>
        </div>
      </div>

      <el-alert
        v-if="scanError"
        :title="scanError"
        type="error"
        :closable="false"
        show-icon
        class="scan-alert"
      />

      <el-alert
        v-else-if="scanWarningsText"
        :title="scanWarningsText"
        type="warning"
        :closable="false"
        show-icon
        class="scan-alert"
      />

      <el-alert
        title="自动扫描仅处理 500MB 以内的 core 文件；超过 500MB 时建议上传日志进行解析。"
        type="info"
        :closable="false"
        show-icon
        class="scan-alert"
      />

      <div v-if="scannedCandidates.length > 0" class="scan-list">
        <div v-for="candidate in scannedCandidates" :key="candidate.sourcePath" class="scan-item">
          <div class="scan-item-main">
            <div class="scan-item-title-row">
              <div class="scan-item-title">{{ candidate.taskNameSuggestion || candidate.sourceName }}</div>
              <div class="scan-badges">
                <el-tag size="small" effect="plain" round>{{ candidate.sourceType }}</el-tag>
                <el-tag
                  size="small"
                  effect="plain"
                  round
                  :type="candidate.matched ? 'success' : 'warning'"
                >
                  {{ candidate.matched ? '已匹配 ELF' : '待手工匹配' }}
                </el-tag>
              </div>
            </div>

            <div class="scan-path">{{ candidate.sourcePath }}</div>

            <div class="scan-match" v-if="candidate.execPath">
              <span class="scan-match-label">ELF</span>
              <span class="scan-match-path">{{ candidate.execPath }}</span>
              <span class="scan-match-meta">规则: {{ candidate.matchRule }} / 分数: {{ candidate.matchScore }}</span>
            </div>
            <div class="scan-match scan-match-missing" v-else>
              尚未自动匹配到 ELF，请检查命名规则或后续补充手动选择能力。
            </div>
          </div>

          <div class="scan-item-actions">
            <el-button
              type="primary"
              :disabled="!candidate.matched"
              :loading="creatingSourcePath === candidate.sourcePath"
              @click="createTaskByCandidate(candidate)"
            >
              创建任务
            </el-button>
          </div>
        </div>
      </div>

      <el-empty
        v-else-if="!scanLoading"
        description="当前扫描目录下没有发现可解析的 core 文件"
      />
    </el-card>

    <div class="workspace-grid">
      <el-card shadow="never" class="workspace-card">
        <template #header>
          <div class="card-header">
            <div>
              <div class="card-title">手动上传工作台</div>
              <div class="card-subtitle">选择分析来源后，上传主文件与 ELF 并创建任务。</div>
            </div>
            <div class="mode-switch" role="tablist" aria-label="analysis mode">
              <button
                type="button"
                :class="['mode-pill', { active: mode === 'gdb' }]"
                @click="switchMode('gdb')"
              >
                <el-icon><Document /></el-icon>
                GDB 日志
              </button>
              <button
                type="button"
                :class="['mode-pill', { active: mode === 'core' }]"
                @click="switchMode('core')"
              >
                <el-icon><Cpu /></el-icon>
                Core Dump
              </button>
            </div>
          </div>
        </template>

        <div class="workspace-body">
          <div class="form-row">
            <label class="field-label" for="task-name">任务名称</label>
            <el-input
              id="task-name"
              v-model="taskName"
              class="task-input"
              :placeholder="taskNamePlaceholder"
              clearable
            />
          </div>

          <div class="upload-grid">
            <section class="upload-slot">
              <div class="slot-header">
                <div class="slot-copy">
                  <div class="slot-title">{{ sourceLabel }}</div>
                  <div class="slot-help">{{ sourceHint }}</div>
                </div>
                <el-tag class="slot-tag" effect="plain" round>{{ mode === 'gdb' ? 'TEXT LOG' : 'ELF CORE' }}</el-tag>
              </div>

              <el-upload
                drag
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handleFirstFileChange"
                :multiple="false"
                :accept="sourceAccept"
                class="upload-dropzone"
              >
                <el-icon class="upload-icon" :size="30"><UploadFilled /></el-icon>
                <div class="upload-title">拖拽或点击选择{{ sourceShortLabel }}</div>
                <div class="upload-copy">{{ sourceAcceptText }}</div>
              </el-upload>

              <div v-if="firstFile" class="selected-file">
                <div class="selected-file-main">
                  <div class="file-avatar">
                    <el-icon><Files /></el-icon>
                  </div>
                  <div class="selected-file-copy">
                    <div class="selected-file-name">{{ firstFile.name }}</div>
                    <div class="selected-file-meta">{{ formatSize(firstFile.size) }}</div>
                  </div>
                </div>
                <el-button text @click="clearFirstFile">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </section>

            <section class="upload-slot">
              <div class="slot-header">
                <div class="slot-copy">
                  <div class="slot-title">ELF 可执行文件</div>
                  <div class="slot-help">用于符号解析和函数定位，建议保留 `-g` 调试信息。</div>
                </div>
                <el-tag class="slot-tag" effect="plain" round>EXECUTABLE</el-tag>
              </div>

              <el-upload
                drag
                :auto-upload="false"
                :show-file-list="false"
                :on-change="handleExecChange"
                :multiple="false"
                accept=".elf,.so,*"
                class="upload-dropzone"
              >
                <el-icon class="upload-icon" :size="30"><UploadFilled /></el-icon>
                <div class="upload-title">拖拽或点击选择 ELF 文件</div>
                <div class="upload-copy">可执行文件、带符号 ELF、或对应共享对象</div>
              </el-upload>

              <div v-if="execFile" class="selected-file">
                <div class="selected-file-main">
                  <div class="file-avatar file-avatar-soft">
                    <el-icon><Cpu /></el-icon>
                  </div>
                  <div class="selected-file-copy">
                    <div class="selected-file-name">{{ execFile.name }}</div>
                    <div class="selected-file-meta">{{ formatSize(execFile.size) }}</div>
                  </div>
                </div>
                <el-button text @click="clearExecFile">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </section>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="summary-card">
        <template #header>
          <div class="summary-header">
            <div class="card-title">提交摘要</div>
            <el-tag :type="readyToSubmit ? 'success' : 'info'" effect="plain" round>
              {{ readyToSubmit ? 'Ready' : 'Pending' }}
            </el-tag>
          </div>
        </template>

        <div class="summary-body">
          <div class="summary-section">
            <div class="summary-label">分析模式</div>
            <div class="summary-value">{{ mode === 'gdb' ? 'GDB 日志分析' : 'Core Dump 分析' }}</div>
          </div>

          <div class="summary-section">
            <div class="summary-label">任务名预览</div>
            <div class="summary-value summary-value-strong">{{ effectiveTaskName }}</div>
          </div>

          <div class="check-list">
            <div :class="['check-item', { done: !!firstFile }]">
              <span class="check-mark">{{ firstFile ? 'OK' : '1' }}</span>
              <div>
                <div class="check-title">{{ sourceLabel }}</div>
                <div class="check-desc">{{ firstFile ? firstFile.name : '尚未选择主分析文件' }}</div>
              </div>
            </div>
            <div :class="['check-item', { done: !!execFile }]">
              <span class="check-mark">{{ execFile ? 'OK' : '2' }}</span>
              <div>
                <div class="check-title">ELF 可执行文件</div>
                <div class="check-desc">{{ execFile ? execFile.name : '尚未选择 ELF 文件' }}</div>
              </div>
            </div>
          </div>

          <div class="summary-actions">
            <el-button type="primary" size="large" :loading="loading" :disabled="!readyToSubmit" @click="submitTask">
              {{ loading ? '创建中...' : '上传并创建任务' }}
            </el-button>
            <el-button size="large" @click="resetAll">清空重来</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <el-card v-if="createdTask" shadow="never" class="result-card">
      <div class="result-content">
        <div>
          <div class="result-title">任务已创建</div>
          <div class="result-subtitle">任务 ID #{{ createdTask.id }}，可以直接跳转查看解析结果。</div>
        </div>
        <div class="result-actions">
          <el-button type="primary" @click="goToTask(createdTask.id)">
            <el-icon><View /></el-icon>
            查看结果
          </el-button>
          <el-button @click="resetAll">继续上传</el-button>
        </div>
      </div>
    </el-card>

    <div v-if="uploading" class="uploading-overlay">
      <el-card shadow="never" class="uploading-card">
        <div class="uploading-label">正在上传并创建任务</div>
        <div class="uploading-text">保持当前页面即可，进度会随上传和任务创建推进。</div>
        <el-progress :percentage="uploadPercent" stroke-width="10" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createTask, createTaskFromScan, scanCandidates as fetchScanCandidates, uploadFile } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()

const mode = ref('gdb')
const firstFile = ref(null)
const execFile = ref(null)
const taskName = ref('')
const loading = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const createdTask = ref(null)

const scanLoading = ref(false)
const autoRefresh = ref(true)
const scanResult = ref(null)
const scanError = ref('')
const creatingSourcePath = ref('')

let firstFileId = null
let execFileId = null
let scanTimer = null

const scannedCandidates = computed(() => scanResult.value?.candidates || [])
const scanWarningsText = computed(() => {
  const warnings = scanResult.value?.warnings || []
  return warnings.length > 0 ? warnings.join('；') : ''
})

const sourceLabel = computed(() => mode.value === 'gdb' ? 'GDB 日志文件' : 'Core Dump 文件')
const sourceShortLabel = computed(() => mode.value === 'gdb' ? 'GDB 日志' : 'Core Dump')
const sourceAccept = computed(() => mode.value === 'gdb' ? '.log,.txt,.out' : '.core,.dump,.crash')
const sourceAcceptText = computed(() => mode.value === 'gdb'
  ? '支持 .log / .txt / .out，推荐使用标准 GDB 脚本导出的日志'
  : '支持 .core / .dump / .crash，上传后由后端继续完成 GDB 解析')
const sourceHint = computed(() => mode.value === 'gdb'
  ? '适合已经拿到 GDB 输出日志的场景。'
  : '适合直接从 Linux 虚拟机导出 core dump 的场景。')
const taskNamePlaceholder = computed(() => {
  const fallback = firstFile.value?.name || execFile.value?.name || '使用主文件名作为默认任务名'
  return `留空则默认使用：${fallback}`
})
const effectiveTaskName = computed(() => taskName.value || firstFile.value?.name || execFile.value?.name || '未命名任务')
const readyToSubmit = computed(() => !!firstFile.value && !!execFile.value && !loading.value)

onMounted(() => {
  refreshScan()
  syncScanTimer()
})

onBeforeUnmount(() => {
  stopScanTimer()
})

watch(autoRefresh, () => {
  syncScanTimer()
})

function syncScanTimer() {
  stopScanTimer()
  if (!autoRefresh.value) return
  scanTimer = window.setInterval(() => {
    refreshScan(true)
  }, 8000)
}

function stopScanTimer() {
  if (scanTimer) {
    window.clearInterval(scanTimer)
    scanTimer = null
  }
}

async function refreshScan(silent = false) {
  if (!silent) {
    scanLoading.value = true
  }
  scanError.value = ''
  try {
    const res = await fetchScanCandidates()
    scanResult.value = res.data
  } catch (err) {
    scanError.value = err.response?.data?.error || err.message || '扫描失败'
  } finally {
    if (!silent) {
      scanLoading.value = false
    }
  }
}

async function createTaskByCandidate(candidate) {
  if (!candidate?.matched) {
    ElMessage.warning('当前候选项还没有自动匹配到 ELF')
    return
  }

  creatingSourcePath.value = candidate.sourcePath
  try {
    const res = await createTaskFromScan(
      candidate.sourcePath,
      candidate.sourceType,
      candidate.execPath,
      candidate.taskNameSuggestion
    )
    createdTask.value = res.data
    ElMessage.success('自动扫描任务创建成功')
    await refreshScan(true)
  } catch (err) {
    ElMessage.error('自动创建任务失败: ' + (err.response?.data?.error || err.message))
  } finally {
    creatingSourcePath.value = ''
  }
}

function switchMode(nextMode) {
  if (mode.value === nextMode) return
  mode.value = nextMode
  clearFirstFile()
  createdTask.value = null
}

function handleFirstFileChange(file) {
  firstFile.value = file.raw
  createdTask.value = null
}

function handleExecChange(file) {
  execFile.value = file.raw
  createdTask.value = null
}

function clearFirstFile() {
  firstFile.value = null
}

function clearExecFile() {
  execFile.value = null
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let index = 0
  let size = bytes
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(1)} ${units[index]}`
}

async function submitTask() {
  if (!firstFile.value || !execFile.value) {
    ElMessage.warning('请先补齐主文件和 ELF 文件')
    return
  }

  loading.value = true
  uploading.value = true
  uploadPercent.value = 12

  try {
    const fileType = mode.value === 'gdb' ? 'GDB_LOG' : 'CORE_DUMP'
    const firstRes = await uploadFile(firstFile.value, fileType)
    firstFileId = firstRes.data.id
    uploadPercent.value = 52

    const execRes = await uploadFile(execFile.value, 'EXECUTABLE')
    execFileId = execRes.data.id
    uploadPercent.value = 82

    const name = effectiveTaskName.value
    const taskRes = await createTask(firstFileId, execFileId, name)
    createdTask.value = taskRes.data
    uploadPercent.value = 100

    ElMessage.success('解析任务创建成功')
  } catch (err) {
    ElMessage.error('操作失败: ' + (err.response?.data?.error || err.message))
  } finally {
    loading.value = false
    uploading.value = false
  }
}

function goToTask(id) {
  router.push('/tasks/' + id)
}

function resetAll() {
  mode.value = 'gdb'
  firstFile.value = null
  execFile.value = null
  taskName.value = ''
  createdTask.value = null
  uploadPercent.value = 0
  firstFileId = null
  execFileId = null
}
</script>

<style scoped>
.upload-page {
  display: grid;
  gap: 24px;
}

.scan-card,
.workspace-card,
.summary-card,
.result-card {
  border-radius: 24px;
}

.scan-header,
.card-header,
.summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.scan-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text);
}

.card-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--app-text-muted);
}

.scan-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.scan-stat {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(248, 245, 240, 0.92);
  border: 1px solid rgba(141, 158, 192, 0.08);
}

.scan-alert {
  margin-bottom: 16px;
}

.scan-list {
  display: grid;
  gap: 14px;
}

.scan-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(125, 149, 200, 0.10);
}

.scan-item-main {
  min-width: 0;
}

.scan-item-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.scan-item-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text);
}

.scan-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.scan-path,
.scan-match-path {
  margin-top: 8px;
  font-size: 12px;
  color: var(--app-text-secondary);
  word-break: break-all;
}

.scan-match {
  margin-top: 10px;
  display: grid;
  gap: 4px;
}

.scan-match-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text);
}

.scan-match-meta,
.scan-match-missing {
  font-size: 12px;
  color: var(--app-text-muted);
}

.scan-item-actions {
  display: flex;
  align-items: center;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(300px, 0.9fr);
  gap: 24px;
  align-items: start;
}

.mode-switch {
  display: inline-flex;
  padding: 5px;
  border-radius: 999px;
  background: var(--app-surface-subtle);
  border: 1px solid var(--app-border-strong);
}

.mode-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--app-text-secondary);
  font: inherit;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.18s ease;
}

.mode-pill:hover {
  transform: translateY(-1px);
}

.mode-pill.active {
  background: rgba(125, 149, 200, 0.16);
  color: var(--app-primary-dark);
}

.workspace-body {
  display: grid;
  gap: 24px;
}

.form-row {
  display: grid;
  gap: 10px;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-secondary);
}

.task-input {
  max-width: 480px;
}

.upload-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.upload-slot {
  display: grid;
  gap: 14px;
  padding: 20px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(249, 246, 241, 0.92));
  border: 1px solid rgba(135, 153, 188, 0.14);
}

.slot-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
}

.slot-copy {
  min-width: 0;
}

.slot-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text);
}

.slot-help {
  margin-top: 6px;
  min-height: 44px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-muted);
  text-wrap: pretty;
}

.slot-tag {
  align-self: start;
  justify-self: end;
}

.upload-dropzone :deep(.el-upload-dragger) {
  padding: 26px 18px;
  border-radius: 18px;
  border: 1px dashed rgba(128, 151, 194, 0.34);
  background: rgba(255, 255, 255, 0.72);
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.upload-dropzone :deep(.el-upload-dragger:hover) {
  border-color: rgba(121, 147, 204, 0.65);
  background: rgba(250, 250, 255, 0.84);
  transform: translateY(-1px);
}

.upload-icon {
  margin-bottom: 10px;
  color: var(--app-primary);
}

.upload-icon :deep(svg),
.file-avatar :deep(svg),
.selected-file :deep(.el-icon) {
  display: block;
}

.upload-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text);
}

.upload-copy {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--app-text-muted);
  text-wrap: pretty;
}

.selected-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(125, 149, 200, 0.10);
}

.selected-file-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.file-avatar {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: #6f84b1;
  background: rgba(127, 149, 200, 0.16);
}

.file-avatar-soft {
  color: #80998f;
  background: rgba(146, 180, 171, 0.18);
}

.selected-file-copy {
  min-width: 0;
}

.selected-file-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.selected-file-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.summary-body {
  display: grid;
  gap: 18px;
}

.summary-section {
  display: grid;
  gap: 6px;
}

.summary-label {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--app-text-muted);
}

.summary-value {
  font-size: 15px;
  line-height: 1.5;
  color: var(--app-text);
  text-wrap: pretty;
}

.summary-value-strong {
  font-weight: 600;
}

.check-list {
  display: grid;
  gap: 12px;
}

.check-item {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(248, 245, 240, 0.9);
  border: 1px solid rgba(141, 158, 192, 0.10);
}

.check-item.done {
  background: rgba(240, 246, 243, 0.94);
  border-color: rgba(137, 170, 151, 0.18);
}

.check-mark {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(125, 149, 200, 0.16);
  color: var(--app-primary-dark);
  font-size: 13px;
  font-weight: 700;
}

.check-item.done .check-mark {
  background: rgba(136, 171, 154, 0.22);
  color: #64836f;
}

.check-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text);
}

.check-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--app-text-muted);
  text-wrap: pretty;
}

.summary-actions {
  display: grid;
  gap: 12px;
}

.summary-actions :deep(.el-button) {
  width: 100%;
}

.result-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.result-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text);
}

.result-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--app-text-muted);
}

.result-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.uploading-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(243, 239, 232, 0.54);
  backdrop-filter: blur(6px);
  z-index: 1000;
}

.uploading-card {
  width: min(92vw, 360px);
  padding: 24px;
  border-radius: 22px;
}

.uploading-label {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text);
}

.uploading-text {
  margin: 8px 0 16px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--app-text-muted);
}

@media (max-width: 1080px) {
  .scan-summary,
  .workspace-grid,
  .upload-grid {
    grid-template-columns: 1fr;
  }

  .scan-item {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .scan-header,
  .card-header,
  .summary-header,
  .result-content {
    flex-direction: column;
    align-items: stretch;
  }

  .scan-actions {
    justify-content: space-between;
  }

  .upload-slot {
    padding: 22px 18px;
  }

  .mode-switch {
    width: 100%;
  }

  .mode-pill {
    flex: 1;
    justify-content: center;
  }

  .result-actions {
    width: 100%;
  }

  .result-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
