import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 120000, // 2分钟
})

// 文件上传
export function uploadFile(file, fileType) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('fileType', fileType)
  return api.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 文件列表
export function listFiles(page = 1, size = 20) {
  return api.get('/files/list', { params: { page, size } })
}

// 创建解析任务
export function createTask(gdbLogFileId, execFileId, taskName) {
  return api.post('/tasks/create', { gdbLogFileId, execFileId, taskName })
}

// 固定目录自动扫描候选
export function scanCandidates() {
  return api.get('/scan/candidates')
}

// 基于固定目录中的本地文件创建任务
export function createTaskFromScan(sourcePath, sourceType, execPath, taskName) {
  return api.post('/scan/create-task', {
    sourcePath,
    sourceType,
    execPath,
    taskName,
  })
}

// 任务列表
export async function listTasks(page = 1, size = 20) {
  const res = await api.get('/tasks/list', { params: { page, size } })
  // 支持分页 Page 格式和旧版 List 格式
  if (res.data && res.data.records !== undefined) {
    return { records: res.data.records, total: res.data.total }
  }
  return { records: res.data, total: res.data.length }
}

// 任务详情
export function getTask(id) {
  return api.get(`/tasks/${id}`)
}

// 解析结果
export function getTaskResult(id) {
  return api.get(`/tasks/${id}/result`)
}

// 删除任务
export function deleteTask(id) {
  return api.delete(`/tasks/${id}`)
}

export default api
