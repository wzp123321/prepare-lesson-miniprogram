<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

// 当前激活菜单：用完整 path 匹配 el-menu :default-active / :active
const activeMenu = computed(() => route.path)

interface MenuGroup {
  groupTitle: string
  items: { path: string; title: string }[]
}

// 左导航分「管理」「看板」两组（来自前端概设 §3.1）
const menuGroups: MenuGroup[] = [
  {
    groupTitle: '管理',
    items: [
      { path: '/admin/students', title: '学生管理' },
      { path: '/admin/time-slots', title: '时间段管理' },
      { path: '/admin/courses', title: '课程管理' },
      { path: '/admin/dicts', title: '字典管理' },
      { path: '/admin/schedule', title: '排课系统' }
    ]
  },
  {
    groupTitle: '看板',
    items: [
      { path: '/dashboard/today', title: '今日视图' },
      { path: '/dashboard/overview', title: '数据总览' },
      { path: '/dashboard/monthly', title: '当月课程表' },
      { path: '/dashboard/make-up', title: '待补课' }
    ]
  }
]

function handleSelect(path: string): void {
  router.push(path)
}
</script>

<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="app-logo">备课排课</div>
      <el-menu :default-active="activeMenu" class="app-menu" @select="handleSelect">
        <template v-for="grp in menuGroups" :key="grp.groupTitle">
          <el-menu-item-group :title="grp.groupTitle">
            <el-menu-item
              v-for="item in grp.items"
              :key="item.path"
              :index="item.path"
            >
              {{ item.title }}
            </el-menu-item>
          </el-menu-item-group>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span class="app-header-title">{{ (route.meta.title as string) || '备课排课管理系统' }}</span>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}
.app-aside {
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
}
.app-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 16px;
  color: #409eff;
  border-bottom: 1px solid #e4e7ed;
}
.app-menu {
  border-right: none;
}
.app-header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}
.app-header-title {
  font-size: 16px;
  font-weight: 600;
}
.app-main {
  background: #f0f2f5;
}
</style>
