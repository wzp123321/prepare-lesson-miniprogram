<script setup lang="ts">
// S04 排课系统（Wave 3 实现）
// 晋升 prototype/S04_排课网格.vue：左学生列表(搜索+拖拽) / 右当月网格(自绘 HTML5 拖拽)。
// 对接真实 api（src/api/lesson.ts BE-API-15~20）；无后端时走模块内 USE_MOCK。
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import html2canvas from 'html2canvas'
import {
  getMonth,
  createLesson,
  deleteLesson,
  updateLesson,
  saveMonth,
  batchCreateLessons,
  copyWeekLessons,
  markAbsent,
  changeLessonStatus,
  fetchStudentExport,
  exportMonthExcel,
  slotLabel,
  cellKey,
  type StudentExport
} from '@/api/lesson'
import { fetchStudents } from '@/api/student'
import { arrangeMakeUp } from '@/api/makeUp'
import { openAiDrawer, aiAppliedTick } from '@/utils/aiDrawer'
import { allowedNextStatuses, STATUS_ACTION_LABEL } from '@/constants/status'
import type { Student, TimeSlot, LessonCell, LessonStatus, AbsentBy, MonthGrid } from '@/types'
import { isConflict, errMsg } from '@/utils/error'

// ===================== 类型与状态机底色 =====================
type ViewState = 'loading' | 'empty' | 'error' | 'populated' | 'edge'

const STATUS_META: Record<LessonStatus, { label: string; bg: string; fg: string }> = {
  UNTAKEN: { label: '未上', bg: '#ffffff', fg: '#606266' },
  NORMAL: { label: '正常上课', bg: '#f0f9eb', fg: '#67c23a' },
  ABSENT: { label: '顺延', bg: '#fef0f0', fg: '#f56c6c' },
  MADEUP: { label: '已补', bg: '#eef2ff', fg: '#6366f1' },
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
const draggingLessonId = ref<number | null>(null)
/** 当前拖拽类型：create=左侧拖学生建课，move=拖动已排单元改时段；dragover 时读不到 dataTransfer，需单独记 */
const draggingMode = ref<'create' | 'move' | null>(null)
/** 当前悬停的格子 key（落点高亮） */
const dragOverKey = ref<string | null>(null)
/** 建课/移动/删除进行中，期间禁止再次拖放，避免重复提交 */
const busy = ref(false)
/** 刚变更的格子 key，短暂高亮后自动消失（替代每条操作都弹 toast） */
const flashKey = ref<string | null>(null)
const saving = ref(false)

// 出图弹窗
const exportVisible = ref(false)
const exportStudentId = ref<number | null>(null)
const exportData = ref<StudentExport | null>(null)
const exporting = ref(false)
const exportRef = ref<HTMLElement | null>(null)

// 批量/循环排课弹窗
const batchVisible = ref(false)
const batching = ref(false)
const batchForm = reactive<{
  studentId: number | null
  slotId: number | null
  weekdays: number[]
  dateRange: string[]
}>({
  studentId: null,
  slotId: null,
  weekdays: [],
  dateRange: []
})
const WEEKDAY_OPTIONS = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 }
]

// ===================== 工具 =====================
function currentMonthStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
function weekdayCN(dateStr: string): string {
  const names = ['日', '一', '二', '三', '四', '五', '六']
  return '周' + names[new Date(dateStr).getDay()]
}
/** 今天（yyyy-MM-dd），用于网格高亮当天 */
function todayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
/** 周六 / 周日 */
function isWeekend(day: string): boolean {
  const dow = new Date(day).getDay()
  return dow === 0 || dow === 6
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
const slots = computed<TimeSlot[]>(() => grid.value.slots)

/** 本月一节排课都没有的在读学生（提醒漏排） */
const unscheduledStudents = computed<Student[]>(() => {
  const scheduled = new Set(Object.values(lessons).map((c) => c.studentId))
  return students.value.filter((s) => s.status === 1 && !scheduled.has(s.id))
})

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
/** 未知状态兜底为「未上」，避免 STATUS_META 取不到值时读 .bg 崩溃 */
function statusMeta(status: LessonStatus): { label: string; bg: string; fg: string } {
  return STATUS_META[status] ?? STATUS_META.UNTAKEN
}
/** 单元格基础尺寸与拖拽反馈；真实边框与状态底色交给 CSS（.cell-card） */
function cellStyle(date: string, slotId: number): Record<string, string> {
  const key = cellKey(date, slotId)
  const les = lessonAt(date, slotId)
  const base: Record<string, string> = {
    position: 'relative',
    boxSizing: 'border-box',
    height: '58px',
    padding: '4px',
    transition: 'background-color .18s ease, box-shadow .18s ease'
  }
  // 落点高亮：可放描蓝边；已占用（拖动自身除外）描红边表示放不下
  if (dragOverKey.value === key) {
    const isSelf = draggingMode.value === 'move' && les?.id === draggingLessonId.value
    if (les && !isSelf) {
      return {
        ...base,
        background: '#fff1f1',
        boxShadow: 'inset 0 0 0 1.5px var(--danger)',
        cursor: 'not-allowed'
      }
    }
    return { ...base, background: 'var(--brand-50)', boxShadow: 'inset 0 0 0 1.5px var(--brand-500)' }
  }
  // 变更刚落库：绿框短暂高亮，不弹 toast
  if (flashKey.value === key) {
    return { ...base, background: '#f2fbee', boxShadow: 'inset 0 0 0 1.5px var(--success)' }
  }
  // 点选排课：当前月且已选中学生时，空格提示可点（鼠标/触屏都可用，不必依赖拖拽）
  if (!les && isCurrentMonth.value && selectedStudentId.value != null) {
    return { ...base, background: '#fff', cursor: 'copy' }
  }
  return base
}

/** 由排课 id + 本地入参拼出前端展示用 LessonCell（补全学生名与时段标签） */
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

/** 建课（拖入与点选共用）：含同日重复确认，返回是否真的建入 */
async function createLessonAt(studentId: number, date: string, slotId: number): Promise<boolean> {
  const key = cellKey(date, slotId)
  if (lessons[key]) return false
  // 同日重复：该生当天已有课时先确认一次（一天两节本身是合理场景，不做硬拦）
  const sameDay = Object.values(lessons).filter(
    (c) => c.studentId === studentId && c.lessonDate === date
  )
  if (sameDay.length > 0) {
    const name = studentOf(studentId)?.name ?? '该学生'
    const slotText = sameDay.map((c) => c.slotLabel).join('、')
    try {
      await ElMessageBox.confirm(
        `${name} 在 ${date} 已排 ${sameDay.length} 节：${slotText}。确定再排一节吗？`,
        '同日重复排课',
        { confirmButtonText: '仍然排入', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return false
    }
  }
  const id = await createLesson({ studentId, slotId, lessonDate: date })
  lessons[key] = buildCell({ id, studentId, slotId, lessonDate: date, status: 'UNTAKEN' })
  flash(key)
  return true
}

// ===================== 点选排课 + 单元格操作面板 =====================

/** 日期 → yyyy-MM-dd */
function fmtDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

/** 取某日所在周的周一（一周起点统一为周一） */
function mondayOf(d: Date): Date {
  const t = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  t.setDate(t.getDate() - ((t.getDay() + 6) % 7))
  return t
}

/** 点击网格单元格：有课 → 打开操作面板；空格 → 用已选中学生直接排入 */
function onCellClick(day: string, slotId: number): void {
  if (!isCurrentMonth.value || busy.value) return
  const cell = lessonAt(day, slotId)
  if (cell) {
    openCellDialog(cell)
    return
  }
  const sid = selectedStudentId.value
  if (sid == null) {
    ElMessage.info('请先在左侧选择学生，再点格子排课')
    return
  }
  const stu = studentOf(sid)
  if (stu && stu.status !== 1) {
    ElMessage.warning('该学生已暂停，不可排课')
    return
  }
  void quickArrange(sid, day, slotId)
}

/** 点选排课：选中学生 + 点空格 = 建课 */
async function quickArrange(studentId: number, date: string, slotId: number): Promise<void> {
  busy.value = true
  try {
    await createLessonAt(studentId, date, slotId)
  } catch (err) {
    if (isConflict(err)) ElMessage.warning('该时段已被占用（每格仅 1 人），已拦截冲突')
    else ElMessage.error(errMsg(err))
  } finally {
    busy.value = false
  }
}

/** 本地补丁写入单元格（状态类接口后端均返回 Result<Void>，状态由本地入参补） */
function patchCell(cell: LessonCell, patch: Partial<LessonCell>): void {
  const key = cellKey(cell.lessonDate, cell.slotId)
  if (lessons[key]) lessons[key] = { ...lessons[key], ...patch }
}

const cellDialog = ref(false)
const cellForm = reactive({
  cell: null as LessonCell | null,
  mark: 'NORMAL' as LessonStatus,
  absentBy: 'student' as AbsentBy,
  reason: '',
  makeUpDate: ''
})

/** 合法目标状态取自全局收敛点 constants/status.ts（与后端状态机对齐，避免提交后 400） */
const cellStatusOptions = computed<{ value: LessonStatus; label: string }[]>(() =>
  allowedNextStatuses(cellForm.cell?.status).map((v) => ({ value: v, label: STATUS_ACTION_LABEL[v] }))
)

function openCellDialog(cell: LessonCell): void {
  cellForm.cell = cell
  cellForm.absentBy = cell.absentBy ?? 'student'
  cellForm.reason = cell.absentReason ?? ''
  cellForm.makeUpDate = ''
  const opts = cellStatusOptions.value
  cellForm.mark = opts.length ? opts[0].value : cell.status
  cellDialog.value = true
}

async function submitCellStatus(): Promise<void> {
  const cell = cellForm.cell
  if (!cell || !cellStatusOptions.value.length || busy.value) return
  busy.value = true
  try {
    if (cellForm.mark === 'ABSENT') {
      if (!cellForm.reason.trim()) {
        ElMessage.warning('顺延时必须填写原因')
        return
      }
      await markAbsent(cell.id, {
        absentBy: cellForm.absentBy,
        absentReason: cellForm.reason.trim()
      })
      patchCell(cell, {
        status: 'ABSENT',
        absentBy: cellForm.absentBy,
        absentReason: cellForm.reason.trim()
      })
    } else if (cellForm.mark === 'MADEUP') {
      if (!cellForm.makeUpDate) {
        ElMessage.warning('请选择补课日期')
        return
      }
      await arrangeMakeUp(cell.id, { makeUpDate: cellForm.makeUpDate })
      patchCell(cell, { status: 'MADEUP', makeUpDate: cellForm.makeUpDate })
    } else {
      await changeLessonStatus(cell.id, { status: cellForm.mark })
      patchCell(cell, { status: cellForm.mark, absentBy: null, absentReason: null })
    }
    cellDialog.value = false
    flash(cellKey(cell.lessonDate, cell.slotId))
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    busy.value = false
  }
}

async function onDeleteFromDialog(): Promise<void> {
  const cell = cellForm.cell
  if (!cell) return
  cellDialog.value = false
  await removeLesson(cell.lessonDate, cell.slotId)
}

/** 复制上周课表到本周（按真实日期，周一为一周起点） */
async function onCopyLastWeek(): Promise<void> {
  if (!isCurrentMonth.value || busy.value) return
  const thisMonday = mondayOf(new Date())
  const lastMonday = new Date(thisMonday.getTime() - 7 * 86400000)
  const sourceFrom = fmtDate(lastMonday)
  const targetFrom = fmtDate(thisMonday)
  try {
    await ElMessageBox.confirm(
      `把上周（${sourceFrom} 起 7 天）的课按同星期几复制到本周（${targetFrom} 起 7 天）；已占用或非本月的日期会自动跳过。`,
      '复制上周课表',
      { confirmButtonText: '开始复制', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const r = await copyWeekLessons({ sourceFrom, targetFrom })
    await loadAll()
    if (r.created > 0) {
      ElMessage.success(`已复制 ${r.created} 节${r.skipped ? `，跳过 ${r.skipped} 节` : ''}`)
    } else {
      ElMessage.info(`没有可复制的课（跳过 ${r.skipped} 节）`)
    }
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    busy.value = false
  }
}

// ===================== 拖拽（HTML5 draggable，自绘于 el-* 之上） =====================
function onDragStartStudent(studentId: number, e: DragEvent) {
  draggingStudentId.value = studentId
  draggingLessonId.value = null
  draggingMode.value = 'create'
  e.dataTransfer?.setData('text/plain', String(studentId))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy'
}
function onDragStartCell(cell: LessonCell, e: DragEvent) {
  draggingLessonId.value = cell.id
  draggingStudentId.value = null
  draggingMode.value = 'move'
  e.dataTransfer?.setData('text/plain', `move:${cell.id}:${cell.studentId}`)
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
/** 拖拽结束（放下或中途取消）统一清状态，避免半透明与高亮残留 */
function onDragEnd(): void {
  draggingStudentId.value = null
  draggingLessonId.value = null
  draggingMode.value = null
  dragOverKey.value = null
}
function onDragEnter(date: string, slotId: number): void {
  if (!isCurrentMonth.value || busy.value || !draggingMode.value) return
  dragOverKey.value = cellKey(date, slotId)
}
function onDragLeave(date: string, slotId: number): void {
  if (dragOverKey.value === cellKey(date, slotId)) dragOverKey.value = null
}
function onDragOver(e: DragEvent) {
  if (!isCurrentMonth.value || busy.value || !draggingMode.value) return
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = draggingMode.value === 'move' ? 'move' : 'copy'
  }
}
/** 变更成功后的格子短暂高亮，替代「每拖一次弹一次 toast」 */
function flash(key: string): void {
  flashKey.value = key
  window.setTimeout(() => {
    if (flashKey.value === key) flashKey.value = null
  }, 800)
}
async function onDrop(date: string, slotId: number, e: DragEvent) {
  e.preventDefault()
  onDragEnd()
  if (!isCurrentMonth.value) {
    ElMessage.warning('历史月份只读，不可排课')
    return
  }
  if (busy.value) return
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
    // 后端 /lessons/update 只返回成功标志（Result<Void>），单元格按原数据搬到新格
    const oldKey = Object.keys(lessons).find((k) => lessons[k].id === lessonId)
    if (oldKey === undefined || oldKey === key) return // 原地放下不做无用请求
    const old = lessons[oldKey]
    busy.value = true
    try {
      await updateLesson(lessonId, { slotId, lessonDate: date })
      delete lessons[oldKey]
      const sl = slots.value.find((s) => s.id === slotId)
      lessons[cellKey(date, slotId)] = {
        ...old,
        slotId,
        lessonDate: date,
        slotLabel: sl ? slotLabel(sl) : old.slotLabel
      }
      flash(key)
    } catch (err) {
      if (isConflict(err)) ElMessage.warning('该时段已被占用（每格仅 1 人），已拦截冲突')
      else ElMessage.error(errMsg(err))
    } finally {
      busy.value = false
    }
    return
  }
  // 建课：从左侧拖入学生
  const studentId = Number(raw)
  if (lessons[key]) {
    ElMessage.warning('该时段已被占用（每格仅 1 人），已拦截冲突')
    return
  }
  busy.value = true
  try {
    await createLessonAt(studentId, date, slotId)
  } catch (err) {
    if (isConflict(err)) ElMessage.warning('该时段已被占用（每格仅 1 人），已拦截冲突')
    else ElMessage.error(errMsg(err))
  } finally {
    busy.value = false
  }
}

async function removeLesson(date: string, slotId: number) {
  const key = cellKey(date, slotId)
  const cell = lessons[key]
  if (!cell || busy.value) return
  busy.value = true
  try {
    await deleteLesson(cell.id)
    delete lessons[key]
    ElMessage.info('已移出该排课')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    busy.value = false
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

// ===================== 批量/循环排课 =====================
function openBatch(): void {
  const [y, m] = monthPicker.value.split('-').map(Number)
  const lastDay = new Date(y, m, 0).getDate()
  batchForm.studentId = selectedStudentId.value ?? null
  batchForm.slotId = slots.value[0]?.id ?? null
  batchForm.weekdays = []
  batchForm.dateRange = [`${monthPicker.value}-01`, `${monthPicker.value}-${String(lastDay).padStart(2, '0')}`]
  batchVisible.value = true
}
async function submitBatch(): Promise<void> {
  if (
    !batchForm.studentId ||
    !batchForm.slotId ||
    batchForm.weekdays.length === 0 ||
    batchForm.dateRange.length !== 2
  ) {
    ElMessage.warning('请选择学生、时段、至少一个星期和日期区间')
    return
  }
  batching.value = true
  try {
    const res = await batchCreateLessons({
      studentId: batchForm.studentId,
      slotId: batchForm.slotId,
      weekdays: batchForm.weekdays,
      startDate: batchForm.dateRange[0],
      endDate: batchForm.dateRange[1]
    })
    batchVisible.value = false
    await loadAll()
    if (res.skipped > 0) {
      const dates = res.skippedDates.slice(0, 5).join('、')
      ElMessage.warning(
        `已排 ${res.created} 节，跳过 ${res.skipped} 节（该时段已有课或非当前月）：${dates}${res.skippedDates.length > 5 ? ' …' : ''}`
      )
    } else {
      ElMessage.success(`已排 ${res.created} 节`)
    }
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    batching.value = false
  }
}

/** 请假方展示名（tooltip 用） */
function absentByLabel(v: AbsentBy | null): string {
  if (v === 'student') return '学生'
  if (v === 'teacher') return '老师'
  return v ?? ''
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
    // 该生本月无排课时后端不返回时段，退回页面当前时段列表，保证出图表头完整
    if (data.slots.length === 0) data.slots = slots.value.map((s) => ({ ...s }))
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

// ===================== 导出 Excel（走后端接口，EasyExcel 出流） =====================
async function onExportExcel(): Promise<void> {
  exporting.value = true
  try {
    const { year, month } = padMonth(monthPicker.value)
    const blob = await exportMonthExcel({ year, month })
    // 后端异常返回 JSON（被当作 blob 收到），转文本取出 message
    if (blob.type && blob.type.includes('json')) {
      let msg = '导出失败'
      try {
        msg = JSON.parse(await blob.text()).message || msg
      } catch {
        // 解析失败则用默认提示
      }
      throw new Error(msg)
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `排课表_${monthPicker.value}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出 Excel')
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
    border: '1px solid var(--border-subtle)',
    minHeight: '40px',
    padding: '4px 6px',
    boxSizing: 'border-box',
    textAlign: 'center',
    fontSize: '12px'
  }
  if (!cell) return { ...base, background: '#fff' }
  const meta = statusMeta(cell.status)
  return { ...base, background: meta.bg, color: meta.fg }
}

onMounted(loadAll)

// 全局悬浮框（智能排课助手）执行完动作后，刷新当前网格
watch(aiAppliedTick, () => loadAll())
</script>

<template>
  <div class="schedule-page">
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
      <div class="toolbar">
        <el-date-picker
          v-model="monthPicker"
          type="month"
          value-format="YYYY-MM"
          format="YYYY年MM月"
          placeholder="选择月份"
          @change="onMonthChange"
          style="width: 150px"
        />
        <el-tag v-if="!isCurrentMonth" type="info" effect="plain">历史月份 · 只读</el-tag>
        <span v-if="unscheduledStudents.length" class="unscheduled">
          本月未排课 {{ unscheduledStudents.length }} 人：
          <span
            v-for="s in unscheduledStudents"
            :key="s.id"
            class="unscheduled-name"
            @click="selectedStudentId = s.id"
            >{{ s.name }}</span
          >
        </span>
        <span class="toolbar-spacer"></span>
        <el-button :loading="exporting" @click="onExportExcel">导出 Excel</el-button>
        <el-button :disabled="!isCurrentMonth" @click="onOpenExport">按学生出图</el-button>
        <el-button :disabled="!isCurrentMonth" @click="openBatch">批量排课</el-button>
        <el-button
          :disabled="!isCurrentMonth"
          title="把上周的课按同星期几复制到本周（已占用 / 非本月自动跳过）"
          @click="onCopyLastWeek"
        >
          复制上周
        </el-button>
        <el-button type="primary" plain @click="openAiDrawer(monthPicker)">智能排课</el-button>
        <el-button
          type="primary"
          :disabled="!isCurrentMonth"
          :loading="saving"
          title="拖拽排课即时保存；本按钮仅关闭上月顺延课（视为已安排进本月）"
          @click="onSave"
        >
          结算上月顺延
        </el-button>
      </div>

      <!-- 三栏 -->
      <el-row :gutter="12">
        <!-- 左：学生列表 -->
        <el-col :span="5">
          <el-card shadow="never" class="tight-card">
            <el-input
              v-model="keyword"
              placeholder="搜索姓名/年级"
              clearable
              size="default"
              style="margin-bottom: 8px"
            />
            <!-- 点选排课提示：选中学生后可直接点网格空格排入（触屏亦可用） -->
            <div class="pick-hint" :class="{ active: selectedStudentId != null }">
              <template v-if="selectedStudentId != null">
                已选 <b>{{ studentOf(selectedStudentId)?.name }}</b> · 点网格空格即可排课
              </template>
              <template v-else>点学生选中后，可直接点格子排课</template>
            </div>
            <div class="student-list">
              <div
                v-for="s in filteredStudents"
                :key="s.id"
                :draggable="isCurrentMonth && s.status === 1"
                @dragstart="onDragStartStudent(s.id, $event)"
                @dragend="onDragEnd"
                @click="selectedStudentId = s.id"
                :style="{
                  borderLeft: `4px solid ${s.color}`,
                  background: selectedStudentId === s.id ? 'var(--brand-50)' : '#fff',
                  padding: '8px 10px',
                  marginBottom: '6px',
                  borderRadius: '4px',
                  cursor: 'grab',
                  opacity: draggingStudentId === s.id ? 0.4 : s.status === 0 ? 0.5 : 1
                }"
              >
                <div style="font-weight: 600">
                  {{ s.name }}
                  <el-tag size="small" :type="s.status === 1 ? 'success' : 'info'">
                    {{ s.status === 1 ? '在读' : '暂停' }}
                  </el-tag>
                </div>
                <div style="font-size: 12px; color: var(--text-muted)">{{ s.grade }} · ¥{{ s.price }}/节</div>
              </div>
            </div>
          </el-card>
        </el-col>

        <!-- 中：当月网格 -->
        <el-col :span="19">
          <el-card shadow="never" class="tight-card">
            <div class="grid-scroll">
              <table class="grid" style="width: 100%; border-collapse: separate; border-spacing: 0">
                <thead>
                  <tr>
                    <th class="corner">日期＼时段</th>
                    <th v-for="sl in slots" :key="sl.id" class="head">{{ slotLabel(sl) }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="day in grid.days" :key="day">
                    <td
                      class="rowhead"
                      :class="{ today: day === todayStr(), weekend: isWeekend(day) }"
                    >
                      <div class="day-num">{{ day.slice(5) }}</div>
                      <div class="day-week">{{ weekdayCN(day) }}</div>
                    </td>
                    <td
                      v-for="sl in slots"
                      :key="sl.id"
                      :style="cellStyle(day, sl.id)"
                      @click="onCellClick(day, sl.id)"
                      @dragenter="onDragEnter(day, sl.id)"
                      @dragleave="onDragLeave(day, sl.id)"
                      @dragover="onDragOver"
                      @drop="onDrop(day, sl.id, $event)"
                    >
                      <el-tooltip
                        v-if="lessonAt(day, sl.id)"
                        placement="top"
                        effect="light"
                        :show-after="200"
                      >
                        <template #content>
                          <div>
                            {{ lessonAt(day, sl.id)!.studentName }} ·
                            {{ statusMeta(lessonAt(day, sl.id)!.status).label }}
                          </div>
                          <div v-if="lessonAt(day, sl.id)!.absentBy">
                            请假方：{{ absentByLabel(lessonAt(day, sl.id)!.absentBy) }}
                          </div>
                          <div v-if="lessonAt(day, sl.id)!.absentReason">
                            原因：{{ lessonAt(day, sl.id)!.absentReason }}
                          </div>
                          <div v-if="lessonAt(day, sl.id)!.makeUpDate">
                            补课：{{ lessonAt(day, sl.id)!.makeUpDate }}
                          </div>
                          <div v-if="lessonAt(day, sl.id)!.remark">
                            备注：{{ lessonAt(day, sl.id)!.remark }}
                          </div>
                        </template>
                        <div
                          class="cell-card"
                          :class="`st-${lessonAt(day, sl.id)!.status}`"
                          :draggable="isCurrentMonth"
                          @dragstart="onDragStartCell(lessonAt(day, sl.id)!, $event)"
                          @dragend="onDragEnd"
                        >
                          <span
                            class="cell-bar"
                            :style="{ background: studentOf(lessonAt(day, sl.id)!.studentId)?.color }"
                          ></span>
                          <span class="cell-name">{{ lessonAt(day, sl.id)!.studentName }}</span>
                          <span
                            v-if="studentOf(lessonAt(day, sl.id)!.studentId)?.grade"
                            class="cell-grade"
                          >
                            {{ studentOf(lessonAt(day, sl.id)!.studentId)?.grade }}
                          </span>
                          <span
                            v-if="isCurrentMonth"
                            class="del"
                            @click.stop="removeLesson(day, sl.id)"
                            >×</span
                          >
                        </div>
                      </el-tooltip>
                    </td>
                  </tr>
                </tbody>
              </table>
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
                <div style="font-size: 11px; color: var(--text-muted)">{{ weekdayCN(day) }}</div>
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

    <!-- 批量/循环排课弹窗 -->
    <el-dialog v-model="batchVisible" title="批量排课" width="560px">
      <el-form label-width="90px">
        <el-form-item label="学生">
          <el-select v-model="batchForm.studentId" placeholder="请选择学生" filterable style="width: 100%">
            <el-option
              v-for="s in students"
              :key="s.id"
              :label="`${s.name}${s.status === 1 ? '' : '（已暂停）'}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时段">
          <el-select v-model="batchForm.slotId" placeholder="请选择时段" style="width: 100%">
            <el-option v-for="sl in slots" :key="sl.id" :label="slotLabel(sl)" :value="sl.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="每周">
          <el-checkbox-group v-model="batchForm.weekdays">
            <el-checkbox v-for="w in WEEKDAY_OPTIONS" :key="w.value" :value="w.value" :label="w.value">
              {{ w.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="日期区间">
          <el-date-picker
            v-model="batchForm.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <div style="font-size: 12px; color: var(--text-muted)">
        按所选星期在日期区间内循环建课；该时段已有课或非当前月的日期会自动跳过。
      </div>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batching" @click="submitBatch">开始排课</el-button>
      </template>
    </el-dialog>

    <!-- 单元格操作面板：点已有课的格子打开（不必再跳到「当月课程表」改状态） -->
    <el-dialog v-model="cellDialog" title="课次操作" width="440px">
      <template v-if="cellForm.cell">
        <el-descriptions :column="1" size="small" border style="margin-bottom: 12px">
          <el-descriptions-item label="学生">{{ cellForm.cell.studentName }}</el-descriptions-item>
          <el-descriptions-item label="日期">
            {{ cellForm.cell.lessonDate }} {{ weekdayCN(cellForm.cell.lessonDate) }}
          </el-descriptions-item>
          <el-descriptions-item label="时段">{{ cellForm.cell.slotLabel }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <span :style="{ color: statusMeta(cellForm.cell.status).fg, fontWeight: 500 }">
              {{ statusMeta(cellForm.cell.status).label }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="cellForm.cell.absentReason" label="顺延原因">
            {{ cellForm.cell.absentReason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="cellForm.cell.makeUpDate" label="补课日期">
            {{ cellForm.cell.makeUpDate }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-width="80px">
          <el-form-item v-if="cellStatusOptions.length" label="改为">
            <el-radio-group v-model="cellForm.mark">
              <el-radio v-for="o in cellStatusOptions" :key="o.value" :value="o.value">
                {{ o.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="cellForm.mark === 'ABSENT'">
            <el-form-item label="请假方">
              <el-select v-model="cellForm.absentBy" style="width: 100%">
                <el-option label="学生请假" value="student" />
                <el-option label="老师请假" value="teacher" />
              </el-select>
            </el-form-item>
            <el-form-item label="原因" required>
              <el-input
                v-model="cellForm.reason"
                type="textarea"
                :rows="2"
                placeholder="必填：顺延原因"
              />
            </el-form-item>
          </template>
          <template v-else-if="cellForm.mark === 'MADEUP'">
            <el-form-item label="补课日期" required>
              <el-date-picker
                v-model="cellForm.makeUpDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择补课日期"
              />
            </el-form-item>
          </template>
          <el-alert
            v-if="!cellStatusOptions.length"
            type="info"
            :closable="false"
            show-icon
            title="该课次已是终态（正常上课 / 已补 / 作废），状态不可再变更"
          />
        </el-form>
      </template>
      <template #footer>
        <div style="display: flex; align-items: center">
          <el-button type="danger" plain @click="onDeleteFromDialog">删除本节</el-button>
          <span style="flex: 1"></span>
          <el-button @click="cellDialog = false">
            {{ cellStatusOptions.length ? '取消' : '关闭' }}
          </el-button>
          <el-button
            v-if="cellStatusOptions.length"
            type="primary"
            :loading="busy"
            @click="submitCellStatus"
          >
            保存
          </el-button>
        </div>
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
  background: var(--surface-sunken);
  width: 90px;
}
.grid .head {
  background: var(--surface-sunken);
  padding: 6px 4px;
  white-space: nowrap;
}
.grid .rowhead {
  background: var(--surface-sunken);
  width: 90px;
  padding: 4px;
}
.del {
  position: absolute;
  top: 4px;
  right: 6px;
  cursor: pointer;
  color: var(--danger);
  font-weight: 700;
  font-size: 13px;
  line-height: 1;
  opacity: 0.22;
  transition: opacity 0.15s ease;
}
.cell-card:hover .del {
  opacity: 1;
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
/* 顶部工具条（紧凑，把空间让给网格） */
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.toolbar-spacer {
  flex: 1;
}
.unscheduled {
  font-size: 12px;
  color: var(--text-muted);
}
.unscheduled-name {
  color: var(--brand-500);
  cursor: pointer;
  margin-right: 6px;
}
.unscheduled-name:hover {
  text-decoration: underline;
}
/* 去掉卡片标题与内边距，内容区更大 */
.tight-card :deep(.el-card__body) {
  padding: 10px 12px;
}
/* 页面纵向撑满：工具条定高，网格吃掉剩余高度 */
.schedule-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.schedule-page :deep(.el-row) {
  flex: 1;
  min-height: 0;
}
.schedule-page :deep(.el-col) {
  height: 100%;
}
.schedule-page :deep(.el-card) {
  height: 100%;
}
.schedule-page :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
/* 学生列表跟随卡片高度，长名单内部滚动 */
.student-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
/* 网格可视区：吃掉卡片剩余高度，表头/首列在容器内吸顶吸左 */
.grid-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

/* ===== 排课网格（现代看板风格；作用域限定 .grid-scroll，不影响出图预览表） ===== */
.grid-scroll .grid {
  border: none;
}
.grid-scroll .grid th,
.grid-scroll .grid td {
  border: none;
  border-right: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
  text-align: center;
  font-size: 12px;
  user-select: none;
}
.grid-scroll .grid tbody td {
  background: #fff;
  transition: background-color 0.15s ease;
}
.grid-scroll .grid tbody td:hover {
  background: var(--surface-sunken);
}
/* 表头：白底 + 细底线 + 字重（替代原来的整块灰底） */
.grid-scroll .grid thead th {
  position: sticky;
  top: 0;
  z-index: 3;
  background: #fff;
  padding: 10px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-strong);
  letter-spacing: 0.3px;
  white-space: nowrap;
  border-bottom: 1px solid var(--border-base);
}
/* 左上角两向固定，压在两者之上 */
.grid-scroll .grid .corner {
  left: 0;
  z-index: 4;
  width: 96px;
  font-size: 11px;
  font-weight: 400;
  color: var(--text-faint);
}
/* 第一列（日期）吸左 */
.grid-scroll .grid .rowhead {
  position: sticky;
  left: 0;
  z-index: 2;
  width: 96px;
  padding: 0;
  background: #fff;
  border-right: 1px solid var(--border-base);
}
.day-num {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-strong);
  line-height: 1.25;
}
.day-week {
  font-size: 11px;
  color: var(--text-muted);
}
.grid-scroll .grid .rowhead.weekend {
  background: var(--surface-sunken);
}
.grid-scroll .grid .rowhead.today {
  background: var(--brand-50);
}
.grid-scroll .grid .rowhead.today .day-num {
  color: var(--brand-500);
}
.grid-scroll .grid .rowhead.today::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--brand-500);
}

/* 有课的格子：圆角卡片 + 学生色竖条（替代整格填色） */
.cell-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 1px;
  height: 100%;
  padding: 0 10px;
  border-radius: 8px;
  overflow: hidden;
  text-align: left;
  cursor: move;
  transition: filter 0.15s ease;
}
.cell-card:hover {
  filter: brightness(0.975);
}
.cell-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  opacity: 0.9;
}
.cell-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
}
/* 第二行显示年级（原为上课状态文字；状态改由卡片底色 + hover tooltip 表达） */
.cell-grade {
  font-size: 11px;
  line-height: 1.1;
  opacity: 0.72;
}
.cell-card.st-UNTAKEN {
  background: #f6f7f9;
  color: #5f6368;
}
.cell-card.st-NORMAL {
  background: #edf7ea;
  color: #3d8a2c;
}
.cell-card.st-ABSENT {
  background: #fdeded;
  color: #c14f4c;
}
.cell-card.st-MADEUP {
  background: var(--brand-50);
  color: var(--brand-600);
}
.cell-card.st-CANCELLED {
  background: #f4f5f7;
  color: #9aa0a6;
}

/* 点选排课提示条 */
.pick-hint {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-muted);
  padding: 4px 8px;
  margin-bottom: 8px;
  border-radius: 4px;
  background: var(--surface-sunken);
}
.pick-hint.active {
  color: var(--brand-600);
  background: var(--brand-50);
}
.pick-hint b {
  font-weight: 500;
}

</style>
