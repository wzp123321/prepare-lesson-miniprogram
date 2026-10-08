<script setup lang="ts">
// 排课记录（课节）查询页 —— 按学生 / 年级 / 时间范围 / 状态筛选（BE-API-34）
// 「课程管理」是配置层（每生一门课只存单价），这里才是逐节课的记录视图。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { queryLessons } from '@/api/lesson'
import { fetchStudents } from '@/api/student'
import { fetchDicts } from '@/api/dict'
import { STATUS_META, ABSENT_BY_LABEL } from '@/constants/status'
import { errMsg } from '@/utils/error'
import type { Student, Dict, LessonRecord, LessonStatus, AbsentBy } from '@/types'

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const STATUS_ORDER: LessonStatus[] = ['UNTAKEN', 'NORMAL', 'ABSENT', 'MADEUP', 'CANCELLED']

const loading = ref(false)
const rows = ref<LessonRecord[]>([])
const total = ref(0)
const students = ref<Student[]>([])
const grades = ref<Dict[]>([])

const query = reactive({
  studentId: null as number | null,
  grade: null as string | null,
  dateRange: null as [string, string] | null,
  status: null as LessonStatus | null,
  page: 1,
  size: 20
})

/** 未知状态兜底为「未上」，避免取值崩溃 */
function statusMeta(s: LessonStatus) {
  return STATUS_META[s] ?? STATUS_META.UNTAKEN
}

function slotText(r: LessonRecord): string {
  return r.slotStart && r.slotEnd ? `${r.slotStart.slice(0, 5)}-${r.slotEnd.slice(0, 5)}` : '—'
}

function weekdayText(r: LessonRecord): string {
  return r.weekday ? WEEKDAYS[r.weekday - 1] : ''
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const res = await queryLessons({
      studentId: query.studentId,
      grade: query.grade,
      from: query.dateRange?.[0] ?? null,
      to: query.dateRange?.[1] ?? null,
      status: query.status,
      page: query.page,
      size: query.size
    })
    rows.value = res.list ?? []
    total.value = res.total ?? 0
  } catch (e) {
    ElMessage.error(errMsg(e))
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.page = 1
  loadList()
}

function onReset(): void {
  query.studentId = null
  query.grade = null
  query.dateRange = null
  query.status = null
  query.page = 1
  loadList()
}

function onPageChange(p: number): void {
  query.page = p
  loadList()
}

function onSizeChange(s: number): void {
  query.size = s
  query.page = 1
  loadList()
}

onMounted(async () => {
  try {
    const [stuRes, gradeRes] = await Promise.all([
      fetchStudents({ page: 1, size: 1000 }),
      fetchDicts('grade')
    ])
    students.value = stuRes.list ?? []
    grades.value = gradeRes ?? []
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
  loadList()
})
</script>

<template>
  <el-card>
    <div class="filters">
      <el-select
        v-model="query.studentId"
        placeholder="全部学生"
        clearable
        filterable
        style="width: 180px"
      >
        <el-option
          v-for="s in students"
          :key="s.id"
          :label="s.grade ? `${s.name}（${s.grade}）` : s.name"
          :value="s.id"
        />
      </el-select>
      <el-select v-model="query.grade" placeholder="全部年级" clearable style="width: 140px">
        <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
      </el-select>
      <el-date-picker
        v-model="query.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="~"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px"
      />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
        <el-option v-for="s in STATUS_ORDER" :key="s" :label="statusMeta(s).label" :value="s" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <div class="count-hint">共 {{ total }} 条排课记录</div>

    <el-table v-loading="loading" :data="rows" empty-text="没有符合条件的排课记录">
      <el-table-column label="日期" min-width="140">
        <template #default="{ row }">
          <span class="date">{{ row.lessonDate }}</span>
          <span class="weekday">{{ weekdayText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="时段" min-width="120" align="center">
        <template #default="{ row }">{{ slotText(row) }}</template>
      </el-table-column>
      <el-table-column label="学生" min-width="110">
        <template #default="{ row }">
          <span class="name">{{ row.studentName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="年级" min-width="100">
        <template #default="{ row }">
          <span v-if="row.grade">{{ row.grade }}</span>
          <span v-else class="muted">未填</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).tagType" size="small" effect="light">
            {{ statusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="请假 / 补课" min-width="210">
        <template #default="{ row }">
          <span v-if="row.absentReason" class="muted">
            {{ row.absentBy ? ABSENT_BY_LABEL[row.absentBy as AbsentBy] : '' }}：{{
              row.absentReason
            }}
          </span>
          <span v-if="row.makeUpDate" class="muted">补课 {{ row.makeUpDate }}</span>
          <span v-if="!row.absentReason && !row.makeUpDate" class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.page"
      :page-sizes="[20, 50, 100]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />
  </el-card>
</template>

<style scoped>
.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 14px;
}
.count-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 10px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.date {
  font-weight: 500;
}
.weekday {
  margin-left: 6px;
  font-size: 12px;
  color: var(--text-muted);
}
.name {
  font-weight: 500;
}
.muted {
  color: var(--text-muted);
}
</style>
