import { createRouter, createWebHistory } from 'vue-router'
import UploadPage from '../views/UploadPage.vue'
import TaskListPage from '../views/TaskListPage.vue'
import TaskDetailPage from '../views/TaskDetailPage.vue'

const routes = [
  { path: '/', name: 'Upload', component: UploadPage },
  { path: '/tasks', name: 'TaskList', component: TaskListPage },
  { path: '/tasks/:id', name: 'TaskDetail', component: TaskDetailPage, props: true },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
