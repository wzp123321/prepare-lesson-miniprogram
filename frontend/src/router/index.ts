import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'

// 路由表 —— 与前端概设 §3.1 完全一致
// 管理组：后台录入；看板组：前台查看。落地页 = 看板「今日视图」。
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard/today',
    children: [
      // ===== 管理组（后台录入）=====
      {
        path: 'admin/students',
        name: 'StudentList',
        component: () => import('@/views/StudentListView.vue'),
        meta: { group: 'admin', title: '学生管理' }
      },
      {
        path: 'admin/time-slots',
        name: 'TimeSlotList',
        component: () => import('@/views/TimeSlotListView.vue'),
        meta: { group: 'admin', title: '时间段管理' }
      },
      {
        path: 'admin/courses',
        name: 'CourseList',
        component: () => import('@/views/CourseListView.vue'),
        meta: { group: 'admin', title: '课程管理' }
      },
      {
        path: 'admin/dicts',
        name: 'DictList',
        component: () => import('@/views/DictListView.vue'),
        meta: { group: 'admin', title: '字典管理' }
      },
      {
        path: 'admin/schedule',
        name: 'Schedule',
        component: () => import('@/views/admin/ScheduleView.vue'),
        meta: { group: 'admin', title: '排课系统' }
      },
      // ===== 看板组（前台查看）=====
      {
        path: 'dashboard/today',
        name: 'DashboardToday',
        component: () => import('@/views/dashboard/TodayView.vue'),
        meta: { group: 'dashboard', title: '今日视图' }
      },
      {
        path: 'dashboard/overview',
        name: 'DashboardOverview',
        component: () => import('@/views/dashboard/OverviewView.vue'),
        meta: { group: 'dashboard', title: '数据总览' }
      },
      {
        path: 'dashboard/monthly',
        name: 'DashboardMonthly',
        component: () => import('@/views/dashboard/MonthlyView.vue'),
        meta: { group: 'dashboard', title: '当月课程表' }
      },
      {
        path: 'dashboard/make-up',
        name: 'DashboardMakeUp',
        component: () => import('@/views/dashboard/MakeUpView.vue'),
        meta: { group: 'dashboard', title: '待补课' }
      }
    ]
  },
  // 兜底：未知路径重定向到落地页
  { path: '/:pathMatch(.*)*', redirect: '/dashboard/today' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
