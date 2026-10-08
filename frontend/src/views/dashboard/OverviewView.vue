<script setup lang="ts">
// S08 数据总览 —— Wave 4 实现
// el-date-picker 选月查询；顶部 el-card 总览卡片（排 N/已上 X/顺延 Y 拆 学生a+老师b/已补 Z/作废 W/待补 K）；
// 月收入 el-statistic；历史月未上课明细 el-table（学生/日期/时段/原因）。历史月只读。
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { LessonStatus, AbsentBy } from '@/types'
import { fetchMonthStatistic, fetchMonthDetail, fetchMonthIncome, type MonthDetailRow } from '@/api/statistics'
import { STATUS_META, ABSENT_BY_LABEL } from '@/constants/status'
import { errMsg } from '@/utils/error'

function currentMonthStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const monthPicker = ref<string>(currentMonthStr())
const loading = ref(false)
const statistic = ref<{
  scheduled: number
  normal: number
  absent: number
  absentByStudent: number
  absentByTeacher: number
  madeUp: number
  cancelled: number
  pending: number
} | null>(null)
const income = ref<{ income: number; formula: string } | null>(null)
const detail = ref<MonthDetailRow[]>([])

function padParams() {
  const [y, m] = monthPicker.value.split('-').map(Number)
  return { year: y, month: m }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const params = padParams()
    const [stat, inc, det] = await Promise.all([
      fetchMonthStatistic(params),
      fetchMonthIncome(params),
      fetchMonthDetail(params)
    ])
    statistic.value = stat
    income.value = inc
    detail.value = det
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    loading.value = false
  }
}

function onMonthChange(): void {
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap">
      <el-date-picker
        v-model="monthPicker"
        type="month"
        value-format="YYYY-MM"
        format="YYYY年MM月"
        placeholder="选择月份"
        @change="onMonthChange"
        style="width: 160px"
      />
      <el-tag type="info" effect="plain">历史月份 · 只读</el-tag>
    </div>

    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else-if="statistic">
      <!-- 总览指标 -->
      <el-row :gutter="12" class="metrics">
        <el-col :span="4">
          <el-card shadow="never">
            <div class="metric-label">排课<span class="metric-code">N</span></div>
            <div class="metric">{{ statistic.scheduled }}</div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never">
            <div class="metric-label">已上<span class="metric-code">X</span></div>
            <div class="metric" style="color: var(--success)">{{ statistic.normal }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="metric-label">顺延<span class="metric-code">Y</span></div>
            <div class="metric" style="color: var(--danger)">{{ statistic.absent }}</div>
            <div class="sub">
              学生请假 {{ statistic.absentByStudent }} ／ 老师请假 {{ statistic.absentByTeacher }}
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never">
            <div class="metric-label">已补<span class="metric-code">Z</span></div>
            <div class="metric" style="color: var(--brand-500)">{{ statistic.madeUp }}</div>
          </el-card>
        </el-col>
        <el-col :span="3">
          <el-card shadow="never">
            <div class="metric-label">作废<span class="metric-code">W</span></div>
            <div class="metric" style="color: var(--text-muted)">{{ statistic.cancelled }}</div>
          </el-card>
        </el-col>
        <el-col :span="3">
          <el-card shadow="never">
            <div class="metric-label">待补<span class="metric-code">K</span></div>
            <div class="metric" style="color: var(--warning)">{{ statistic.pending }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 月收入 -->
      <el-card shadow="never" style="margin-bottom: 12px" header="月收入">
        <el-statistic
          v-if="income"
          :value="income.income"
          prefix="¥"
          :precision="0"
        />
        <div v-if="income" class="sub">口径：{{ income.formula }}</div>
      </el-card>

      <!-- 历史月未上课明细 -->
      <el-card shadow="never" header="历史月未上课明细">
        <el-table :data="detail" size="small">
          <el-table-column prop="studentName" label="学生" width="120" />
          <el-table-column prop="lessonDate" label="日期" width="140" />
          <el-table-column prop="slot" label="时段" width="130" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }: { row: MonthDetailRow }">
              <el-tag :type="STATUS_META[row.status as LessonStatus].tagType" size="small">
                {{ STATUS_META[row.status as LessonStatus].label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="请假方" width="100">
            <template #default="{ row }: { row: MonthDetailRow }">
              <span v-if="row.absentBy">{{ ABSENT_BY_LABEL[row.absentBy as AbsentBy] }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="absentReason" label="原因" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.metrics :deep(.el-card__body) {
  padding: 14px 16px;
}
.metric-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-muted);
}
.metric-code {
  padding: 0 4px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunken);
  font-size: 10px;
  color: var(--text-faint);
}
.metric {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.2;
}
.sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
