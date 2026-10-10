<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import AiScheduleFab from "@/components/AiScheduleFab.vue";

const route = useRoute();
const router = useRouter();

// 当前激活菜单：用完整 path 匹配 el-menu :default-active
const activeMenu = computed(() => route.path);

interface MenuLeaf {
  path: string;
  title: string;
}

interface MenuNode {
  /** 一级索引（无 path 时作为可折叠分组的 key） */
  key: string;
  title: string;
  /** 有 path = 一级直达项；无 path + children = 可折叠一级分组 */
  path?: string;
  children?: MenuLeaf[];
}

// 左导航：一级可折叠 + 二级跳转（来自前端概设 §3.1，2026-10-08 改为两级结构便于查看）
const menuTree: MenuNode[] = [
  {
    key: "admin",
    title: "教学管理",
    children: [
      { path: "/admin/students", title: "学生管理" },
      { path: "/admin/courses", title: "课程管理" },
      { path: "/admin/time-slots", title: "时间段管理" },
      { path: "/admin/dicts", title: "字典管理" },
    ],
  },
  {
    key: "schedule",
    title: "排课系统",
    path: "/admin/schedule",
  },
  {
    key: "prep",
    title: "备课",
    children: [
      { path: "/prep/todo", title: "待备课" },
      { path: "/prep/papers", title: "试卷" },
      { path: "/prep/questions", title: "题库" },
      { path: "/prep/kps", title: "知识点" },
    ],
  },
  {
    key: "dashboard",
    title: "教学看板",
    children: [
      { path: "/dashboard/todo", title: "今日待办" },
      { path: "/dashboard/today", title: "今日视图" },
      { path: "/dashboard/monthly", title: "当月课程表" },
      { path: "/dashboard/records", title: "排课记录" },
      { path: "/dashboard/make-up", title: "待补课" },
      { path: "/dashboard/overview", title: "数据总览" },
    ],
  },
];

// 默认展开所有一级分组（用户可手动折叠，AdminLayout 不随路由重建，折叠状态会保留）
const defaultOpeneds = menuTree
  .filter((n) => n.children?.length)
  .map((n) => n.key);

function handleSelect(path: string): void {
  if (path) router.push(path);
}
</script>

<template>
  <el-container class="app-layout">
    <el-aside width="224px" class="app-aside">
      <div class="app-logo">
        <span class="logo-mark">课</span>
        <span class="logo-text">备课排课</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        class="app-menu"
        @select="handleSelect"
      >
        <template v-for="node in menuTree" :key="node.key">
          <!-- 一级直达（如排课系统） -->
          <el-menu-item
            v-if="node.path"
            :index="node.path"
            class="menu-root-item"
          >
            {{ node.title }}
          </el-menu-item>
          <!-- 一级分组 + 二级菜单（可折叠） -->
          <el-sub-menu v-else :index="node.key">
            <template #title>
              <span class="menu-root-title">{{ node.title }}</span>
            </template>
            <el-menu-item
              v-for="leaf in node.children"
              :key="leaf.path"
              :index="leaf.path"
            >
              {{ leaf.title }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
    <!-- 全局智能排课助手：右下角悬浮球 + 右侧抽屉 -->
    <AiScheduleFab />
  </el-container>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}

/* ===================== 侧栏（深色） ===================== */
.app-aside {
  background: var(--sidebar-bg);
  /* 菜单项多于视口时整体滚动：用 overflow-y:auto 而非 hidden，
     否则二级菜单展开后底部几项会被裁掉、又滚不到（问题 1）。 */
  overflow-x: hidden;
  overflow-y: auto;
}
.app-logo {
  height: var(--logo-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  color: var(--text-on-brand);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.3px;
  border-bottom: 1px solid var(--sidebar-border);
}
.logo-mark {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--brand-500);
  font-size: 14px;
  font-weight: 600;
}
.app-menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--sidebar-text);
  --el-menu-hover-bg-color: var(--sidebar-hover);
  --el-menu-active-color: var(--text-on-brand);
  --el-menu-item-height: 42px;
  padding: 8px 0;
  border-right: none;
}
.app-menu :deep(.el-menu-item),
.app-menu :deep(.el-sub-menu__title) {
  margin: 2px 10px;
  border-radius: var(--radius-sm);
}
/* 选中项实心高亮，比只变文字色更醒目 */
.app-menu :deep(.el-menu-item.is-active) {
  background: var(--sidebar-active);
  color: var(--text-on-brand);
}
/* 二级菜单缩进、字号略小 */
.app-menu :deep(.el-sub-menu .el-menu-item) {
  --el-menu-item-height: 38px;
  padding-left: 44px !important;
  font-size: 13.5px;
}
/* 一级加粗、二级常规，层级一眼可辨 */
.menu-root-title,
.menu-root-item {
  font-weight: 600;
  font-size: 14.5px;
}

/* ===================== 内容区 ===================== */
.app-main {
  background: var(--page-bg);
  display: flex;
  flex-direction: column;
}
/* 页面根元素自动撑满 el-main 剩余高度（内容超出时照常撑高、由 el-main 滚动） */
.app-main > * {
  flex: 1;
}
/* 卡片纵向排布，内容区吃掉剩余高度，底部不再留大片空白 */
.app-main :deep(.el-card) {
  display: flex;
  flex-direction: column;
}
.app-main :deep(.el-card__body) {
  flex: 1;
}
</style>
