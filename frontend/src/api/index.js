import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

export function uploadFile(file, fileType) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('fileType', fileType)
  return api.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listFiles(page = 1, size = 20) {
  return api.get('/files/list', { params: { page, size } })
}

export function createTask(gdbLogFileId, execFileId, taskName) {
  return api.post('/tasks/create', { gdbLogFileId, execFileId, taskName })
}

export function scanCandidates() {
  return api.get('/scan/candidates')
}

export function createTaskFromScan(sourcePath, sourceType, execPath, taskName) {
  return api.post('/scan/create-task', {
    sourcePath,
    sourceType,
    execPath,
    taskName,
  })
}

export async function listTasks(page = 1, size = 20) {
  const res = await api.get('/tasks/list', { params: { page, size } })
  if (res.data && res.data.records !== undefined) {
    return { records: res.data.records, total: res.data.total }
  }
  return { records: res.data, total: res.data.length }
}

export function getTask(id) {
  return api.get(`/tasks/${id}`)
}

export function getTaskResult(id) {
  return api.get(`/tasks/${id}/result`)
}

export function deleteTask(id) {
  return api.delete(`/tasks/${id}`)
}

export default api
