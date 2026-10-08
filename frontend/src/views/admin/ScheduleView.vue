<script setup lang="ts">
// S04 排课系统（Wave 3 实现）
// 晋升 prototype/S04_排课网格.vue：左学生列表(搜索+拖拽) / 中当月网格(自绘 HTML5 拖拽) / 右学生信息。
// 对接真实 api（src/api/lesson.ts BE-API-15~20）；无后端时走模块内 USE_MOCK。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import html2canvas from 'html2canvas'
import {
  getMonth,
  createLesson,
  deleteLesson,
  updateLesson,
  saveMonth,
  fetchStudentExport,
  slotLabel,
  cellKey,
  type StudentExport
} from '@/api/lesson'
import { fetchStudents } from '@/api/student'
import type { Student, TimeSlot, LessonCell, LessonStatus, MonthGrid } from '@/types'
import { isConflict, errMsg } from '@/api/mockData'

// ===================== 类型与状态机底色 =====================
type ViewState = 'loading' | 'empty' | 'error' | 'populated' | 'edge'

const STATUS_META: Record<LessonStatus, { label: string; bg: string; fg: string }> = {
  UNTAKEN: { label: '未上', bg: '#ffffff', fg: '#606266' },
  NORMAL: { label: '正常上课', bg: '#f0f9eb', fg: '#67c23a' },
  ABSENT: { label: '顺延', bg: '#fef0f0', fg: '#f56c6c' },
  MADEUP: { label: '已补', bg: '#ecf5ff', fg: '#409eff' },
  CANCELLED: { label: '作废', bg: '#f4f4f5', fg: '#909399' }
}

// ===================== 数据 =====================
const viewState = ref<ViewState>('loading')
const errorMessage = ref('')
const students = ref<Student[]>([])
const grid = ref<MonthGrid>({ days: [], slots: [], cells: {} })
const lessons = reactive<Record<string, LessonCell>>({}) // key = `${date}#${slotId}`
const selectedStudentId = ref<number | null>(null)
const monthPicker = ref<string>(currentMonthStr())
const keyword = ref('')
const draggingStudentId = ref<number | null>(null)
const saving = ref(false)

// 出图弹窗
const exportVisible = ref(false)
const exportStudentId = ref<number | null>(null)
const exportData = ref<StudentExport | null>(null)
const exporting = ref(false)
const exportRef = ref<HTMLElement | null>(null)

// ===================== 工具 =====================
function currentMonthStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
function weekdayCN(dateStr: string): string {
  const names = ['日', '一', '二', '三', '四', '五', '六']
  return '周' + names[new Date(dateStr).getDay()]
}
function padMonth(value: string): { year: number; month: number } {
  const [y, monthIdx] = value.split('-').map(Number)
  return { year: y, month: monthIdx }
}

const isCurrentMonth = computed(() => monthPicker.value === currentMonthStr())
const filteredStudents = computed(() => {
  const k = keyword.value.trim()
  if (!k) return students.value
  return students.value.filter((s) => s.name.includes(k) || s.grade.includes(k))
})
const selectedStudent = computed(() => students.value.find((s) => s.id === selectedStudentId.value) || null)
const slots = computed<TimeSlot[]>(() => grid.value.slots)

const gridState = computed<ViewState>(() => {
  if (viewState.value === 'loading' || viewState.value === 'error') return viewState.value
  if (!isCurrentMonth.value) return 'edge'
  if (students.value.length === 0 || grid.value.slots.length === 0) return 'empty'
  return 'populated'
})

// ===================== 单元格 =====================
function lessonAt(date: string, slotId: number): LessonCell | undefined {
  return lessons[cellKey(date, slotId)]
}
function studentOf(id: number | undefined): Student | undefined {
  return students.value.find((s) => s.id === id)
}
function cellStyle(date: string, slotId: number): Record<string, string> {
  const les = lessonAt(date, slotId)
  const base: Record<string, string> = {
    border: '1px solid #ebeef5',
    minHeight: '46px',
    padding: '4px 6px',
    position: 'relative',
    boxSizing: 'border-box'
  }
  if (!les) return { ...base, background: '#fff' }
  const meta = STATUS_META[les.status]
  return { ...base, background: meta.bg, color: meta.fg }
}

/** 由后端 Lesson 拼出前端展示用 LessonCell（建课/改课返回后补全学生名与时段标签） */
function buildCell(lesson: { id: number; studentId: number; slotId: number; lessonDate: string; status: LessonStatus }): LessonCell {
  const stu = studentOf(lesson.studentId)
  const sl = slots.value.find((s) => s.id === lesson.slotId)
  return {
    id: lesson.id,
    studentId: lesson.studentId,
    studentName: stu?.name || '',
    courseId: 0,
    slotId: lesson.slotId,
    slotLabel: sl ? slotLabel(sl) : '',
    lessonDate: lesson.lessonDate,
    status: lesson.status,
    absentBy: null,
    absentReason: null,
    makeUpDate: null,
    closed: false,
    remark: ''
  }
}

// ===================== 拖拽（HTML5 draggable，自绘于 el-* 之上） =====================
function onDragStartStudent(studentId: number, e: DragEvent) {
  draggingStudentId.value = studentId
  e.dataTransfer?.setData('text/plain', String(studentId))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy'
}
function onDragStartCell(cell: LessonCell, e: DragEvent) {
  e.dataTransfer?.setData('text/plain', `move:${cell.id}:${cell.studentId}`)
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
function onDragOver(e: DragEvent) {
  if (!isCurrentMonth.value) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
}
async function onDrop(date: string, slotId: number, e: DragEvent) {
  e.preventDefault()
  if (!isCurrentMonth.value) {
    ElMessage.warning('历史月份只读，不可排课')
    return
  }
  const raw = e.dataTransfer?.getData('text/plain')
  if (!raw) return
  const key = cellKey(date, slotId)
  if (raw.startsWith('move:')) {
    // 改课：移动已有排课到新格
    const [, idStr] = raw.split(':')
    const lessonId = Number(idStr)
    if (lessons[key] && lessons[key].id !== lessonId) {
      ElMessage.warning('该时段已被占用（每格仅 1 人），已拦截冲突')
      return
    }
    try {
      const res = await updateLesson(lessonId, { slotId, lessonDate: date })
      // 移除旧格（key 可能变化）
      Object.keys(lessons).forEach((k) => {
        if (lessons[k].id === lessonId) delete lessons[k]
      })
      lessons[cellKey(date, slotId)] = buildCell(res)
      ElMessage.success('已移动到新时段')
    } catch (err) {
      if (isConflict(err)) ElMessage.warning('该时段已被占用（每格仅 1 人），已拦截冲突')
      else ElMessage.error(errMsg(err))
    }
    return
  }
  // 建课：从左侧拖入学生
  const studentId = Number(raw)
  if (lessons[key]) {
    ElMessage.warning('该时段已被占用（每格仅 1 人），已拦截冲突')
    return
  }
  try {
    const res = await createLesson({ studentId, slotId, lessonDate: date })
    lessons[key] = buildCell(res)
    ElMessage.success('已排入课表（未上，待保存）')
  } catch (err) {
    if (isConflict(err)) ElMessage.warning('该时段已被占用（每格仅 1 人），已拦截冲突')
    else ElMessage.error(errMsg(err))
  }
}

async function removeLesson(date: string, slotId: number) {
  const key = cellKey(date, slotId)
  const cell = lessons[key]
  if (!cell) return
  try {
    await deleteLesson(cell.id)
    delete lessons[key]
    ElMessage.info('已移出该排课')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

// ===================== 加载 =====================
async function loadAll() {
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

async function onMonthChange() {
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

// ===================== 保存（关闭上月顺延） =====================
async function onSave() {
  if (!isCurrentMonth.value) {
    ElMessage.warning('仅当前月可保存排课')
    return
  }
  try {
    await ElMessageBox.confirm(
      '保存时将一并关闭上月所有顺延课（视为已安排进本月）。是否继续？',
      '保存排课',
      { type: 'warning', confirmButtonText: '保存', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  saving.value = true
  try {
    const { year, month } = padMonth(monthPicker.value)
    const res = await saveMonth({ year, month })
    ElMessage.success(`排课已保存；上月顺延课已关闭 ${res.closedCount} 节`)
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

// ===================== 按学生出图（html2canvas → PNG） =====================
async function onOpenExport() {
  exportStudentId.value = null
  exportData.value = null
  exportVisible.value = true
}
async function onExportStudentChange(id: number) {
  const { year, month } = padMonth(monthPicker.value)
  try {
    const data = await fetchStudentExport(id, { year, month })
    exportData.value = data
    if (Object.keys(data.cells).length === 0) {
      ElMessage.info('该生本月暂无排课')
    }
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}
async function onExportPng() {
  if (!exportRef.value) return
  exporting.value = true
  try {
    const canvas = await html2canvas(exportRef.value, { backgroundColor: '#ffffff' })
    const link = document.createElement('a')
    const name = exportData.value?.student.name || 'student'
    link.download = `${name}-${monthPicker.value}-课表.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    ElMessage.success('已导出 PNG')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    exporting.value = false
  }
}
function exportCell(day: string, slotId: number): LessonCell | undefined {
  if (!exportData.value) return undefined
  return exportData.value.cells[cellKey(day, slotId)]
}
function exportCellStyle(cell: LessonCell | undefined): Record<string, string> {
  const base: Record<string, string> = {
    border: '1px solid #ebeef5',
    minHeight: '40px',
    padding: '4px 6px',
    boxSizing: 'border-box',
    textAlign: 'center',
    fontSize: '12px'
  }
  if (!cell) return { ...base, background: '#fff' }
  const meta = STATUS_META[cell.status]
  return { ...base, background: meta.bg, color: meta.fg }
}

onMounted(loadAll)
</script>

<template>
  <div>
    <!-- loading 态 -->
    <el-card v-if="gridState === 'loading'" shadow="never">
      <el-skeleton :rows="8" animated />
    </el-card>

    <!-- error 态 -->
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

    <!-- empty 态 -->
    <el-empty
      v-else-if="gridState === 'empty'"
      description="暂无学生或时间段，请先在管理后台维护基础数据"
    />

    <!-- edge / populated：主体 -->
    <template v-else>
      <!-- 顶部工具条 -->
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
        <el-alert
          v-if="isCurrentMonth"
          type="warning"
          :closable="false"
          show-icon
          title="保存时将关闭上月顺延课（视为已安排进本月）"
          style="flex: 1; min-width: 280px"
        />
        <el-tag v-else type="info" effect="plain">历史月份 · 只读</el-tag>
        <el-button type="primary" :disabled="!isCurrentMonth" :loading="saving" @click="onSave">
          保存
        </el-button>
        <el-button :disabled="!isCurrentMonth" @click="onOpenExport">按学生出图</el-button>
      </div>

      <!-- 三栏 -->
      <el-row :gutter="12">
        <!-- 左：学生列表 -->
        <el-col :span="5">
          <el-card shadow="never" header="学生（拖入网格）">
            <el-input
              v-model="keyword"
              placeholder="搜索姓名/年级"
              clearable
              size="default"
              style="margin-bottom: 8px"
            />
            <div style="max-height: 520px; overflow: auto">
              <div
                v-for="s in filteredStudents"
                :key="s.id"
                :draggable="isCurrentMonth && s.status === 1"
                @dragstart="onDragStartStudent(s.id, $event)"
                @click="selectedStudentId = s.id"
                :style="{
                  borderLeft: `4px solid ${s.color}`,
                  background: selectedStudentId === s.id ? '#ecf5ff' : '#fff',
                  padding: '8px 10px',
                  marginBottom: '6px',
                  borderRadius: '4px',
                  cursor: 'grab',
                  opacity: s.status === 0 ? 0.5 : 1
                }"
              >
                <div style="font-weight: 600">
                  {{ s.name }}
                  <el-tag size="small" :type="s.status === 1 ? 'success' : 'info'">
                    {{ s.status === 1 ? '在读' : '暂停' }}
                  </el-tag>
                </div>
                <div style="font-size: 12px; color: #909399">{{ s.grade }} · ¥{{ s.price }}/节</div>
              </div>
            </div>
          </el-card>
        </el-col>

        <!-- 中：当月网格 -->
        <el-col :span="14">
          <el-card shadow="never" :header="`排课网格 · ${monthPicker}`">
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
                      @dragover="onDragOver"
                      @drop="onDrop(day, sl.id, $event)"
                    >
                      <template v-if="lessonAt(day, sl.id)">
                        <div
                          :draggable="isCurrentMonth"
                          @dragstart="onDragStartCell(lessonAt(day, sl.id)!, $event)"
                          style="display: flex; align-items: center; gap: 4px; cursor: move"
                        >
                          <span
                            :style="{
                              width: '8px',
                              height: '8px',
                              borderRadius: '50%',
                              background: studentOf(lessonAt(day, sl.id)!.studentId)?.color
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
                        <span
                          v-if="isCurrentMonth"
                          class="del"
                          @click.stop="removeLesson(day, sl.id)"
                          >×</span
                        >
                      </template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div style="margin-top: 8px; font-size: 12px; color: #909399">
              说明：从左侧拖入学生生成排课；每格仅 1 人，冲突实时拦截；可拖动已排单元改时段/日期；点 ×
              移除。
            </div>
          </el-card>
        </el-col>

        <!-- 右：学生信息 -->
        <el-col :span="5">
          <el-card shadow="never" header="学生信息">
            <el-empty v-if="!selectedStudent" description="点选左侧学生查看详情" :image-size="60" />
            <div v-else>
              <div style="font-size: 18px; font-weight: 700; margin-bottom: 8px">
                <span
                  :style="{
                    display: 'inline-block',
                    width: '12px',
                    height: '12px',
                    borderRadius: '50%',
                    background: selectedStudent.color,
                    marginRight: '6px'
                  }"
                ></span>
                {{ selectedStudent.name }}
              </div>
              <el-descriptions :column="1" size="small" border>
                <el-descriptions-item label="年级">{{ selectedStudent.grade }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  {{ selectedStudent.status === 1 ? '在读' : '暂停' }}
                </el-descriptions-item>
                <el-descriptions-item label="课单价">¥{{ selectedStudent.price }}/节</el-descriptions-item>
                <el-descriptions-item label="电话">{{ selectedStudent.phone }}</el-descriptions-item>
                <el-descriptions-item label="地址">{{ selectedStudent.address }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- 按学生出图弹窗 -->
    <el-dialog v-model="exportVisible" title="按学生出图" width="720px">
      <el-form :inline="true">
        <el-form-item label="选择学生">
          <el-select
            v-model="exportStudentId"
            placeholder="请选择学生"
            filterable
            style="width: 220px"
            @change="onExportStudentChange"
          >
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="exporting" :disabled="!exportData" @click="onExportPng">
            导出 PNG
          </el-button>
        </el-form-item>
      </el-form>

      <div v-if="exportData" ref="exportRef" class="export-wrap">
        <div class="export-title">
          {{ exportData.student.name }}（{{ exportData.student.grade }}）· {{ monthPicker }} 课表
        </div>
        <table class="grid" :style="{ width: '100%', borderCollapse: 'collapse' }">
          <thead>
            <tr>
              <th class="corner">日期＼时段</th>
              <th v-for="sl in exportData.slots" :key="sl.id" class="head">{{ slotLabel(sl) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="day in exportData.days" :key="day">
              <td class="rowhead">
                <div>{{ day.slice(5) }}</div>
                <div style="font-size: 11px; color: #909399">{{ weekdayCN(day) }}</div>
              </td>
              <td
                v-for="sl in exportData.slots"
                :key="sl.id"
                :style="exportCellStyle(exportCell(day, sl.id))"
              >
                <template v-if="exportCell(day, sl.id)">
                  <div style="display: flex; align-items: center; gap: 4px; justify-content: center">
                    <span
                      :style="{
                        width: '8px',
                        height: '8px',
                        borderRadius: '50%',
                        background: studentOf(exportCell(day, sl.id)!.studentId)?.color
                      }"
                    ></span>
                    <span style="font-weight: 600">{{ exportCell(day, sl.id)!.studentName }}</span>
                  </div>
                  <div>{{ STATUS_META[exportCell(day, sl.id)!.status].label }}</div>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <el-empty v-else description="请选择学生以生成课表" :image-size="60" />
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
.del {
  position: absolute;
  top: 2px;
  right: 4px;
  cursor: pointer;
  color: #f56c6c;
  font-weight: 700;
}
.export-wrap {
  padding: 12px;
  background: #fff;
}
.export-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 8px;
  text-align: center;
}
</style>
