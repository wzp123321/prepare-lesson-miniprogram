<script setup lang="ts">
// S10 当月课程表 —— Wave 4 实现
// 复用 S04 网格概念展示当月课程表（可按学生筛选）；点单元格弹 el-dialog 改状态
// （正常/顺延[弹窗原因]/已补/作废，BE-API-21/22）；历史月只读（lessonDate 不在当前月禁用状态操作）。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { LessonCell, LessonStatus, AbsentBy, MonthGrid, Student } from '@/types'
import { getMonth, markAbsent, changeLessonStatus, cellKey, slotLabel } from '@/api/lesson'
import { arrangeMakeUp } from '@/api/makeUp'
import { fetchStudents } from '@/api/student'
import { STATUS_META } from '@/constants/status'
import { errMsg } from '@/api/mockData'

type ViewState = 'loading' | 'empty' | 'error' | 'populated' | 'edge'

function currentMonthStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
function weekdayCN(dateStr: string): string {
  const names = ['日', '一', '二', '三', '四', '五', '六']
  return '周' + names[new Date(dateStr).getDay()]
}
function padMonth(value: string): { year: number; month: number } {
  const [y, m] = value.split('-').map(Number)
  return { year: y, month: m }
}

const viewState = ref<ViewState>('loading')
const errorMessage = ref('')
const students = ref<Student[]>([])
const grid = ref<MonthGrid>({ days: [], slots: [], cells: {} })
const lessons = reactive<Record<string, LessonCell>>({}) // key = `${date}#${slotId}`
const monthPicker = ref<string>(currentMonthStr())
const filterStudentId = ref<number | null>(null)

const isCurrentMonth = computed(() => monthPicker.value === currentMonthStr())
const slots = computed(() => grid.value.slots)

const gridState = computed<ViewState>(() => {
  if (viewState.value === 'loading' || viewState.value === 'error') return viewState.value
  if (!isCurrentMonth.value) return 'edge'
  if (grid.value.slots.length === 0) return 'empty'
  return 'populated'
})

function lessonAt(date: string, slotId: number): LessonCell | undefined {
  const cell = lessons[cellKey(date, slotId)]
  if (!cell) return undefined
  if (filterStudentId.value != null && cell.studentId !== filterStudentId.value) return undefined
  return cell
}
function studentName(id?: number): string {
  return students.value.find((s) => s.id === id)?.name ?? '未知'
}
function studentColor(id?: number): string {
  return students.value.find((s) => s.id === id)?.color ?? '#909399'
}
function cellStyle(date: string, slotId: number): Record<string, string> {
  const base: Record<string, string> = {
    border: '1px solid #ebeef5',
    minHeight: '46px',
    padding: '4px 6px',
    position: 'relative',
    boxSizing: 'border-box',
    cursor: isCurrentMonth.value ? 'pointer' : 'default'
  }
  const les = lessonAt(date, slotId)
  if (!les) return { ...base, background: '#fff' }
  const meta = STATUS_META[les.status]
  return { ...base, background: meta.bg, color: meta.fg }
}

// ===================== 状态操作弹窗 =====================
const statusDialog = ref(false)
const statusForm = reactive({
  cell: null as LessonCell | null,
  mark: 'NORMAL' as LessonStatus,
  absentBy: 'student' as AbsentBy,
  reason: '',
  makeUpDate: ''
})

function openStatusDialog(cell: LessonCell): void {
  statusForm.cell = cell
  statusForm.mark = cell.status === 'UNTAKEN' ? 'NORMAL' : cell.status
  statusForm.absentBy = cell.absentBy ?? 'student'
  statusForm.reason = cell.absentReason ?? ''
  statusForm.makeUpDate = ''
  statusDialog.value = true
}

function onCellClick(date: string, slotId: number): void {
  if (!isCurrentMonth.value) return // 历史月份只读
  const cell = lessonAt(date, slotId)
  if (!cell) {
    ElMessage.info('该时段无排课')
    return
  }
  openStatusDialog(cell)
}

function applyUpdate(cell: LessonCell, res: { status: LessonStatus; absentBy: AbsentBy | null; absentReason: string | null }): void {
  const key = cellKey(cell.lessonDate, cell.slotId)
  lessons[key] = { ...lessons[key], status: res.status, absentBy: res.absentBy, absentReason: res.absentReason }
}

async function submitStatus(): Promise<void> {
  const cell = statusForm.cell
  if (!cell) return
  try {
    if (statusForm.mark === 'ABSENT') {
      if (!statusForm.reason.trim()) {
        ElMessage.warning('顺延时必须填写原因')
        return
      }
      const res = await markAbsent(cell.id, {
        absentBy: statusForm.absentBy,
        absentReason: statusForm.reason.trim()
      })
      applyUpdate(cell, res)
    } else if (statusForm.mark === 'MADEUP') {
      // 已补走 BE-API-24（写补课日期 + 置 MADEUP + 关单），changeLessonStatus 状态机不允许 ABSENT→MADEUP
      if (!statusForm.makeUpDate) {
        ElMessage.warning('请选择补课日期')
        return
      }
      await arrangeMakeUp(cell.id, { makeUpDate: statusForm.makeUpDate })
      const key = cellKey(cell.lessonDate, cell.slotId)
      lessons[key] = { ...lessons[key], status: 'MADEUP', makeUpDate: statusForm.makeUpDate }
    } else {
      const res = await changeLessonStatus(cell.id, { status: statusForm.mark })
      applyUpdate(cell, res)
    }
    statusDialog.value = false
    ElMessage.success('状态已更新')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

// ===================== 加载 =====================
async function loadAll(): Promise<void> {
  viewState.value = 'loading'
  errorMessage.value = ''
  try {
    const [stuRes, gridRes] = await Promise.all([
      fetchStudents({ page: 1, size: 1000 }),
      getMonth(padMonth(monthPicker.value))
    ])
    students.value = stuRes.list
    grid.value = gridRes
    Object.keys(lessons).forEach((k) => delete lessons[k])
    Object.entries(gridRes.cells).forEach(([k, c]) => (lessons[k] = c))
    viewState.value = 'populated'
  } catch (err) {
    errorMessage.value = errMsg(err)
    viewState.value = 'error'
  }
}

async function onMonthChange(): Promise<void> {
  viewState.value = 'loading'
  try {
    const gridRes = await getMonth(padMonth(monthPicker.value))
    grid.value = gridRes
    Object.keys(lessons).forEach((k) => delete lessons[k])
    Object.entries(gridRes.cells).forEach(([k, c]) => (lessons[k] = c))
    viewState.value = 'populated'
  } catch (err) {
    errorMessage.value = errMsg(err)
    viewState.value = 'error'
  }
}

onMounted(loadAll)
</script>

<template>
  <div>
    <!-- loading -->
    <el-card v-if="gridState === 'loading'" shadow="never">
      <el-skeleton :rows="8" animated />
    </el-card>

    <!-- error -->
    <el-result
      v-else-if="gridState === 'error'"
      icon="error"
      title="加载失败"
      :sub-title="errorMessage"
    >
      <template #extra>
        <el-button type="primary" @click="loadAll">重试</el-button>
      </template>
    </el-result>

    <!-- empty -->
    <el-empty
      v-else-if="gridState === 'empty'"
      description="暂无时间段，请先在管理后台维护时间段"
    />

    <!-- edge / populated -->
    <template v-else>
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
        <el-select
          v-model="filterStudentId"
          placeholder="按学生筛选"
          clearable
          style="width: 180px"
        >
          <el-option label="全部学生" :value="null" />
          <el-option
            v-for="s in students"
            :key="s.id"
            :label="s.name"
            :value="s.id"
          />
        </el-select>
        <el-tag v-if="!isCurrentMonth" type="info" effect="plain">历史月份 · 只读</el-tag>
        <el-tag v-else type="success" effect="plain">当月 · 可管理状态</el-tag>
      </div>

      <el-card shadow="never" :header="`当月课程表 · ${monthPicker}`">
        <div style="overflow: auto">
          <table class="grid" :style="{ width: '100%', borderCollapse: 'collapse' }">
            <thead>
              <tr>
                <th class="corner">日期＼时段</th>
                <th v-for="sl in slots" :key="sl.id" class="head">{{ slotLabel(sl) }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="day in grid.days" :key="day">
                <td class="rowhead">
                  <div>{{ day.slice(5) }}</div>
                  <div style="font-size: 11px; color: #909399">{{ weekdayCN(day) }}</div>
                </td>
                <td
                  v-for="sl in slots"
                  :key="sl.id"
                  :style="cellStyle(day, sl.id)"
                  @click="onCellClick(day, sl.id)"
                >
                  <template v-if="lessonAt(day, sl.id)">
                    <div style="display: flex; align-items: center; gap: 4px">
                      <span
                        :style="{
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          background: studentColor(lessonAt(day, sl.id)!.studentId)
                        }"
                      ></span>
                      <span style="font-weight: 600">{{ lessonAt(day, sl.id)!.studentName }}</span>
                    </div>
                    <el-tag
                      size="small"
                      :style="{
                        color: STATUS_META[lessonAt(day, sl.id)!.status].fg,
                        borderColor: 'transparent',
                        background: 'transparent'
                      }"
                    >
                      {{ STATUS_META[lessonAt(day, sl.id)!.status].label }}
                    </el-tag>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div style="margin-top: 8px; font-size: 12px; color: #909399">
          说明：点击单元格可切换 正常 / 顺延（填原因）/ 已补 / 作废；历史月份只读。
        </div>
      </el-card>
    </template>

    <!-- 状态操作弹窗 -->
    <el-dialog v-model="statusDialog" title="课程状态操作" width="420px">
      <template v-if="statusForm.cell">
        <el-descriptions :column="1" size="small" border style="margin-bottom: 12px">
          <el-descriptions-item label="学生">{{ studentName(statusForm.cell.studentId) }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ statusForm.cell.lessonDate }}</el-descriptions-item>
          <el-descriptions-item label="时段">{{ statusForm.cell.slotLabel }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="STATUS_META[statusForm.cell.status].tagType" size="small">
              {{ STATUS_META[statusForm.cell.status].label }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-width="80px">
          <el-form-item label="新状态">
            <el-radio-group v-model="statusForm.mark">
              <el-radio value="NORMAL">正常上课</el-radio>
              <el-radio value="ABSENT">顺延</el-radio>
              <el-radio value="MADEUP">已补</el-radio>
              <el-radio value="CANCELLED">作废</el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="statusForm.mark === 'ABSENT'">
            <el-form-item label="请假方">
              <el-select v-model="statusForm.absentBy" style="width: 100%">
                <el-option label="学生请假" value="student" />
                <el-option label="老师请假" value="teacher" />
              </el-select>
            </el-form-item>
            <el-form-item label="原因" required>
              <el-input
                v-model="statusForm.reason"
                type="textarea"
                :rows="2"
                placeholder="必填：顺延原因"
              />
            </el-form-item>
          </template>
          <template v-else-if="statusForm.mark === 'MADEUP'">
            <el-form-item label="补课日期" required>
              <el-date-picker
                v-model="statusForm.makeUpDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择补课日期"
              />
            </el-form-item>
          </template>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="statusDialog = false">取消</el-button>
        <el-button type="primary" @click="submitStatus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.grid th,
.grid td {
  text-align: center;
  font-size: 12px;
  user-select: none;
}
.grid .corner {
  background: #f5f7fa;
  width: 90px;
}
.grid .head {
  background: #f5f7fa;
  padding: 6px 4px;
  white-space: nowrap;
}
.grid .rowhead {
  background: #fafafa;
  width: 90px;
  padding: 4px;
}
.el-card {
  border-radius: 8px;
}
</style>
