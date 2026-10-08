<script setup lang="ts">
// S08 数据总览 —— Wave 4 实现
// el-date-picker 选月查询；顶部 el-card 总览卡片（排 N/已上 X/顺延 Y 拆 学生a+老师b/已补 Z/作废 W/待补 K）；
// 月收入 el-statistic；历史月未上课明细 el-table（学生/日期/时段/原因）。历史月只读。
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { LessonStatus } from '@/types'
import { fetchMonthStatistic, fetchMonthDetail, fetchMonthIncome, type MonthDetailRow } from '@/api/statistics'
import { STATUS_META } from '@/constants/status'
import { errMsg } from '@/api/mockData'

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
    detail.value = det.list
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
      <!-- 总览卡片 -->
      <el-row :gutter="12" style="margin-bottom: 12px">
        <el-col :span="4">
          <el-card shadow="never" header="排课 (N)">
            <div class="metric">{{ statistic.scheduled }}</div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never" header="已上 (X)">
            <div class="metric" style="color: #67c23a">{{ statistic.normal }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" header="顺延 (Y)">
            <div class="metric" style="color: #f56c6c">{{ statistic.absent }}</div>
            <div class="sub">
              学生请假 a={{ statistic.absentByStudent }} ／ 老师请假 b={{ statistic.absentByTeacher }}
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never" header="已补 (Z)">
            <div class="metric" style="color: #409eff">{{ statistic.madeUp }}</div>
          </el-card>
        </el-col>
        <el-col :span="3">
          <el-card shadow="never" header="作废 (W)">
            <div class="metric" style="color: #909399">{{ statistic.cancelled }}</div>
          </el-card>
        </el-col>
        <el-col :span="3">
          <el-card shadow="never" header="待补 (K)">
            <div class="metric" style="color: #e6a23c">{{ statistic.pending }}</div>
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
        <el-table :data="detail" border size="small">
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
          <el-table-column prop="absentReason" label="原因" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.el-card {
  border-radius: 8px;
}
.metric {
  font-size: 28px;
  font-weight: 700;
  text-align: center;
}
.sub {
  font-size: 12px;
  color: #909399;
  text-align: center;
  margin-top: 4px;
}
</style>
