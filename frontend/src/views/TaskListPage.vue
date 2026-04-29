<template>
  <div class="task-list-page">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span class="header-title">解析任务历史</span>
          <el-button type="primary" size="small" @click="$router.push('/')">
            <el-icon><Plus /></el-icon>
            新建任务
          </el-button>
        </div>
      </template>

      <!-- Filter Bar -->
      <div class="filter-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索任务名称..."
          clearable
          :prefix-icon="Search"
          style="width: 240px"
          @input="onSearch"
        />
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 130px" @change="loadTasks">
          <el-option label="全部" value="" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="解析中" value="RUNNING" />
          <el-option label="失败" value="FAILED" />
          <el-option label="待处理" value="PENDING" />
        </el-select>
        <span class="filter-total" v-if="total > 0">共 {{ total }} 条记录</span>
      </div>

      <!-- Table -->
      <el-table :data="filteredTasks" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="64" align="center" />
        <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="crashSignal" label="崩溃信号" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.crashSignal" type="danger" effect="plain" size="small">
              {{ row.crashSignal }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalThreads" label="线程数" width="72" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'COMPLETED'" type="success" size="small">
              <el-icon style="margin-right: 3px; vertical-align: middle"><CircleCheckFilled /></el-icon>
              已完成
            </el-tag>
            <el-tag v-else-if="row.status === 'RUNNING'" type="warning" size="small">
              <el-icon class="is-loading" style="margin-right: 3px; vertical-align: middle"><Loading /></el-icon>
              解析中
            </el-tag>
            <el-tag v-else-if="row.status === 'FAILED'" type="danger" size="small">
              <el-icon style="margin-right: 3px; vertical-align: middle"><CircleCloseFilled /></el-icon>
              失败
            </el-tag>
            <el-tag v-else type="info" size="small">
              <el-icon style="margin-right: 3px; vertical-align: middle"><Clock /></el-icon>
              待处理
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="viewTask(row.id)">查看</el-button>
            <el-popconfirm title="确认删除此任务?" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper" v-if="total > size">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next, jumper"
          @current-change="loadTasks"
        />
      </div>

      <el-empty v-if="!loading && filteredTasks.length === 0" description="暂无解析任务" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listTasks, deleteTask } from '../api'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

const router = useRouter()
const tasks = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const searchQuery = ref('')
const statusFilter = ref('')

onMounted(() => loadTasks())

async function loadTasks() {
  loading.value = true
  try {
    const result = await listTasks(page.value, size.value)
    tasks.value = result.records
    total.value = result.total
  } catch (err) {
    ElMessage.error('加载失败: ' + err.message)
  } finally {
    loading.value = false
  }
}

// Client-side filter by name
const filteredTasks = computed(() => {
  let list = tasks.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(t => t.taskName && t.taskName.toLowerCase().includes(q))
  }
  return list
})

function onSearch() {
  // Client-side search, no reload needed
}

function viewTask(id) {
  router.push('/tasks/' + id)
}

async function handleDelete(id) {
  try {
    await deleteTask(id)
    ElMessage.success('已删除')
    loadTasks()
  } catch (err) {
    ElMessage.error('删除失败')
  }
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.task-list-page {
  max-width: 1100px;
  margin: 0 auto;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.filter-total {
  font-size: 13px;
  color: #909399;
  margin-left: auto;
}

.text-muted {
  color: #c0c4cc;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
