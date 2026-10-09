<script setup lang="ts">
// 今日待办（系统落地页）—— BE-API-35 聚合
// 回答老师开机第一个问题「现在该做什么」：把原先散在今日视图 / 待补课 / 待备课三处的
// 待办合成一页。本页只负责「指出要做什么 + 一键跳过去」，具体处理仍走各模块既有页面，
// 避免在待办页里再写一套状态机。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchTodayTodo, TODO_CATEGORY_META } from '@/api/dashboard'
import { fetchMonthStatistic } from '@/api/statistics'
import type { TodoCategory, TodoItem, TodayTodo } from '@/types'
import { STATUS_META } from '@/constants/status'
import { errMsg } from '@/utils/error'

const router = useRouter()

const loading = ref(false)
const data = ref<TodayTodo | null>(null)

/** 本月概况（放在待办页顶部，一眼看进度；失败不影响待办主功能） */
const monthStat = ref<{ scheduled: number; normal: number; absent: number; madeUp: number } | null>(null)

const todayLabel = computed(() => {
  const d = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · ${week}`
})

/** 四类分组按「紧急度」排序：今天要做的在前，今晚要备的在后 */
const groups = computed<{ category: TodoCategory; items: TodoItem[] }[]>(() => {
  const d = data.value
  if (!d) return []
  return [
    { category: 'MARK_TODAY' as TodoCategory, items: d.markToday },
    { category: 'MAKEUP_TODAY' as TodoCategory, items: d.makeUpToday },
    { category: 'MAKEUP_OVERDUE' as TodoCategory, items: d.makeUpOverdue },
    { category: 'PREP_TODAY' as TodoCategory, items: d.prepToday }
  ].filter((g) => g.items.length > 0)
})

const total = computed(() => data.value?.total ?? 0)

/** 待办页只展示，不在此处改状态；点击整行跳到对应页面处理 */
function goto(category: TodoCategory, studentId: number | null): void {
  const meta = TODO_CATEGORY_META[category]
  // 备课页支持按学生定位：带上学生参数，落页后自动选中该生第一节待备课
  router.push({ path: meta.route, query: studentId ? { studentId: String(studentId) } : undefined })
}

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    data.value = await fetchTodayTodo()
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

async function loadMonthStat(): Promise<void> {
  const d = new Date()
  try {
    monthStat.value = await fetchMonthStatistic({ year: d.getFullYear(), month: d.getMonth() + 1 })
  } catch {
    monthStat.value = null // 概况非关键路径，失败静默
  }
}

onMounted(() => {
  loadAll()
  loadMonthStat()
})
</script>

<template>
  <div class="todo-page">
    <div class="page-head">
      <div class="head-left">
        <span class="head-date">{{ todayLabel }}</span>
        <span v-if="!loading" class="head-summary">
          <template v-if="total > 0">今天有 <b class="warn">{{ total }}</b> 件事要处理</template>
          <template v-else>今天全部处理完了，可以歇会儿</template>
        </span>
      </div>
      <div v-if="monthStat" class="head-month">
        本月 排 {{ monthStat.scheduled }} · 已上 {{ monthStat.normal }} · 已补 {{ monthStat.madeUp }} · 顺延
        {{ monthStat.absent }}
      </div>
    </div>

    <el-card v-if="loading" shadow="never">
      <el-skeleton :rows="6" animated />
    </el-card>

    <template v-else>
      <!-- 全部清空 -->
      <el-card v-if="total === 0" shadow="never" class="clear-card">
        <div class="clear-inner">
          <div class="clear-icon">✓</div>
          <div class="clear-title">今天的待办都清空了</div>
          <div class="clear-hint">课程已标记、补课已安排、明天的课也备好了</div>
          <div class="clear-actions">
            <el-button @click="router.push('/admin/schedule')">看看本月课表</el-button>
            <el-button type="primary" @click="router.push('/prep/papers')">去攒点卷子</el-button>
          </div>
        </div>
      </el-card>

      <!-- 四类待办分组 -->
      <el-card v-for="g in groups" :key="g.category" shadow="never" class="group-card">
        <template #header>
          <div class="group-head">
            <div class="group-title">
              {{ TODO_CATEGORY_META[g.category].title }}
              <span class="group-count">{{ g.items.length }}</span>
            </div>
            <div class="group-hint">{{ TODO_CATEGORY_META[g.category].hint }}</div>
          </div>
        </template>

        <div class="item-list">
          <div
            v-for="item in g.items"
            :key="`${g.category}-${item.lessonId}`"
            class="item"
            @click="goto(g.category, item.studentId)"
          >
            <div class="item-main">
              <span class="item-stu">{{ item.studentName || '未指派' }}</span>
              <span v-if="item.grade" class="item-grade">{{ item.grade }}</span>
              <span v-if="item.slot" class="item-slot">{{ item.slot }}</span>
            </div>
            <div class="item-sub">
              <span class="item-date">
                {{ g.category === 'PREP_TODAY' ? '上课 ' : '' }}{{ item.lessonDate || '—' }}
              </span>
              <span class="item-note">{{ item.note }}</span>
            </div>
            <div class="item-right">
              <el-tag
                v-if="item.status !== 'UNTAKEN'"
                size="small"
                effect="plain"
                :type="STATUS_META[item.status]?.tagType"
              >
                {{ STATUS_META[item.status]?.label || item.status }}
              </el-tag>
              <el-button link type="primary" class="item-go">去处理 →</el-button>
            </div>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.todo-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.head-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.head-date {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.head-summary {
  font-size: 13px;
  color: var(--text-muted);
}
.head-summary b.warn {
  color: var(--warning);
  font-size: 15px;
}
.head-month {
  font-size: 12px;
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}

.group-card :deep(.el-card__header) {
  padding: 12px 16px;
}
.group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.group-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.group-count {
  display: inline-block;
  min-width: 20px;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 9px;
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 12px;
  text-align: center;
}
.group-hint {
  font-size: 12px;
  color: var(--text-faint);
}

.item-list {
  display: flex;
  flex-direction: column;
}
.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.item:hover {
  background: var(--surface-sunken);
}
.item + .item {
  border-top: 1px solid var(--border-subtle);
}
.item-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 190px;
}
.item-stu {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.item-grade {
  font-size: 12px;
  color: var(--text-muted);
}
.item-slot {
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.item-sub {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.item-date {
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.item-note {
  font-size: 12px;
  color: var(--text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-right {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}
.item-go {
  font-size: 13px;
}

/* 空态 */
.clear-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}
.clear-inner {
  text-align: center;
}
.clear-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--brand-50);
  color: var(--success);
  font-size: 22px;
}
.clear-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.clear-hint {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-muted);
}
.clear-actions {
  margin-top: 18px;
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>
