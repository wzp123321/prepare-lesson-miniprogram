// S04 排课系统 —— BE-API-15~20（W3-01 / W3-03~06）
//
// 真实路径走 /api/lessons；联调前 USE_MOCK=true 走本地内存数据（见下方 mock 段），
// 与 student.ts / timeSlot.ts 的约定一致，保证无后端时网格可完整演示。
// TODO(W5-03): 后端 Wave B（BE-API-15~20）就绪后将 USE_MOCK 改为 false。
import request from '@/utils/request'
import type {
  MonthGrid,
  Lesson,
  LessonCell,
  LessonStatus,
  AbsentBy,
  TimeSlot,
  Student,
  PageResult,
  LessonRecord
} from '@/types'
import { mockStudents, mockTimeSlots } from './mockData'

const USE_MOCK = false

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v) as string) as T

/** 时间段展示标签 HH:mm-HH:mm（后端 startTime/endTime 为 HH:mm:ss） */
export function slotLabel(s: { startTime: string; endTime: string }): string {
  const fmt = (t: string) => t.slice(0, 5)
  return `${fmt(s.startTime)}-${fmt(s.endTime)}`
}

// ===================== 类型（对齐后端 §2.3 / 前端概设 §2 S04） =====================
/** BE-API-19 保存当月入参：当前年/月（触发关闭上月顺延） */
export interface SaveMonthPayload {
  year: number
  month: number
}

/** BE-API-20 按学生出图返回：该生当月课表 JSON */
export interface StudentExport {
  student: Student
  days: string[]
  slots: TimeSlot[]
  cells: Record<string, LessonCell> // key = `${date}#${slotId}`
}

/**
 * 后端 StudentMonthScheduleVO 原始结构：studentInfo + 扁平 lessons[]，
 * 与前端网格结构（student/days/slots/cells）不同，需适配后再交给视图。
 */
interface RawStudentExport {
  studentInfo: { name: string; color: string; grade: string; price: number }
  lessons: Array<{
    date: string
    slotId: number
    slotStart: string
    slotEnd: string
    status: LessonStatus
    absentBy: AbsentBy | null
    reason: string | null
    makeUpDate: string | null
  }>
}

/** 后端扁平 lessons → 前端网格 StudentExport */
function adaptStudentExport(
  studentId: number,
  params: { year: number; month: number },
  raw: RawStudentExport
): StudentExport {
  const { year, month } = params
  const ym = `${year}-${String(month).padStart(2, '0')}`
  const total = new Date(year, month, 0).getDate()
  const days: string[] = []
  for (let d = 1; d <= total; d++) days.push(`${ym}-${String(d).padStart(2, '0')}`)

  // 后端不单独返回 slots，由 lessons 去重得到，按开始时间升序定 sortOrder
  const slotMap = new Map<number, TimeSlot>()
  raw.lessons.forEach((l) => {
    if (!slotMap.has(l.slotId)) {
      slotMap.set(l.slotId, {
        id: l.slotId,
        startTime: l.slotStart,
        endTime: l.slotEnd,
        sortOrder: slotMap.size + 1,
        enabled: 1
      })
    }
  })
  const slots = [...slotMap.values()]
    .sort((a, b) => a.startTime.localeCompare(b.startTime))
    .map((s, i) => ({ ...s, sortOrder: i + 1 }))

  const cells: Record<string, LessonCell> = {}
  raw.lessons.forEach((l) => {
    cells[cellKey(l.date, l.slotId)] = {
      id: 0, // 出图 VO 未回 lessonId，本页仅用于渲染
      studentId,
      studentName: raw.studentInfo.name,
      courseId: 0,
      slotId: l.slotId,
      slotLabel: slotLabel({ startTime: l.slotStart, endTime: l.slotEnd }),
      lessonDate: l.date,
      status: l.status,
      absentBy: l.absentBy,
      absentReason: l.reason,
      makeUpDate: l.makeUpDate,
      closed: false,
      remark: ''
    }
  })

  return {
    student: {
      id: studentId,
      name: raw.studentInfo.name,
      grade: raw.studentInfo.grade,
      phone: '',
      parentWechat: '',
      address: '',
      price: raw.studentInfo.price,
      status: 1,
      color: raw.studentInfo.color,
      remark: '',
      createTime: '',
      updateTime: ''
    },
    days,
    slots,
    cells
  }
}

// ===================== 单元格 key 工具 =====================
export function cellKey(date: string, slotId: number): string {
  return `${date}#${slotId}`
}

// ===================== mock 内存数据（仅 USE_MOCK 时用） =====================
let lessonSeq = 1000
const mockCells: Record<string, LessonCell> = {}

function seedMonth(yyyymm: string): void {
  const mk = (id: number, studentId: number, slotId: number, day: string, status: LessonStatus): void => {
    const stu = mockStudents.find((s) => s.id === studentId)
    const sl = mockTimeSlots.find((s) => s.id === slotId)
    if (!stu || !sl) return
    const date = `${yyyymm}-${day}`
    mockCells[cellKey(date, slotId)] = {
      id,
      studentId,
      studentName: stu.name,
      courseId: 0,
      slotId,
      slotLabel: slotLabel(sl),
      lessonDate: date,
      status,
      absentBy: null,
      absentReason: null,
      makeUpDate: null,
      closed: false,
      remark: ''
    }
  }
  mk(101, 2, 1, '05', 'NORMAL')
  mk(102, 1, 3, '08', 'ABSENT')
}

// ===================== BE-API-15 当月网格 =====================
export function getMonth(params: { year: number; month: number }): Promise<MonthGrid> {
  if (!USE_MOCK) {
    return request.post<MonthGrid, MonthGrid>('/lessons/month', undefined, { params })
  }
  const { year, month } = params
  const ym = `${year}-${String(month).padStart(2, '0')}`
  seedMonth(ym)
  const total = new Date(year, month, 0).getDate()
  const days: string[] = []
  for (let d = 1; d <= total; d++) days.push(`${ym}-${String(d).padStart(2, '0')}`)
  const slots = clone(mockTimeSlots)
  const cells: Record<string, LessonCell> = {}
  Object.entries(mockCells).forEach(([k, c]) => {
    if (c.lessonDate.startsWith(ym)) cells[k] = clone(c)
  })
  return Promise.resolve({ days, slots, cells })
}

// ===================== BE-API-16 拖拽建课 =====================
// 后端 POST /api/lessons 返回 Result<Long>（新排课 id），不是 Lesson 对象：
// 新建课 status 由后端固定为 UNTAKEN（LessonServiceImpl#createLesson），调用方据此补出单元格。
export function createLesson(data: {
  studentId: number
  slotId: number
  lessonDate: string
}): Promise<number> {
  if (!USE_MOCK) {
    return request.post<number, number>('/lessons', data)
  }
  const key = cellKey(data.lessonDate, data.slotId)
  if (mockCells[key]) {
    const err: { message: string; response: { status: number; data: { message: string } } } = {
      message: '该时段已被占用（每格仅 1 人）',
      response: { status: 409, data: { message: '该时段已被占用（每格仅 1 人）' } }
    }
    return Promise.reject(err)
  }
  const lesson: Lesson = {
    id: ++lessonSeq,
    studentId: data.studentId,
    courseId: 0,
    slotId: data.slotId,
    lessonDate: data.lessonDate,
    status: 'UNTAKEN',
    absentBy: null,
    absentReason: null,
    makeUpDate: null,
    closed: false,
    remark: ''
  }
  const sl = mockTimeSlots.find((s) => s.id === data.slotId)
  const stu = mockStudents.find((s) => s.id === data.studentId)
  if (stu && sl) {
    mockCells[key] = {
      id: lesson.id,
      studentId: lesson.studentId,
      studentName: stu.name,
      courseId: 0,
      slotId: lesson.slotId,
      slotLabel: slotLabel(sl),
      lessonDate: lesson.lessonDate,
      status: lesson.status,
      absentBy: null,
      absentReason: null,
      makeUpDate: null,
      closed: false,
      remark: ''
    }
  }
  return Promise.resolve(lesson.id)
}

// ===================== BE-API-16B 批量/循环排课 =====================
/** 按「学生 + 时段 + 星期几 + 日期区间」批量建课；weekdays 用 1=周一 … 7=周日 */
export interface BatchLessonPayload {
  studentId: number
  slotId: number
  weekdays: number[]
  startDate: string
  endDate: string
}
export interface BatchLessonResult {
  created: number
  skipped: number
  skippedDates: string[]
}
export function batchCreateLessons(data: BatchLessonPayload): Promise<BatchLessonResult> {
  return request.post<BatchLessonResult, BatchLessonResult>('/lessons/batch', data)
}

// BE-API-16C 复制某周课表到另一周（sourceFrom / targetFrom 均为该周周一）
export function copyWeekLessons(data: {
  sourceFrom: string
  targetFrom: string
}): Promise<BatchLessonResult> {
  return request.post<BatchLessonResult, BatchLessonResult>('/lessons/copy-week', data)
}

// ===================== BE-API-34 排课记录查询（学生 / 年级 / 时间范围，分页） =====================
export interface LessonQueryPayload {
  studentId?: number | null
  grade?: string | null
  from?: string | null
  to?: string | null
  status?: string | null
  page?: number
  size?: number
}

export function queryLessons(data: LessonQueryPayload): Promise<PageResult<LessonRecord>> {
  return request.post<PageResult<LessonRecord>, PageResult<LessonRecord>>('/lessons/query', data)
}

// ===================== BE-API-17 删课（拖出/点删） =====================
export function deleteLesson(id: number): Promise<void> {
  if (!USE_MOCK) {
    return request.post<void, void>('/lessons/delete', { id })
  }
  const idx = Object.keys(mockCells).find((k) => mockCells[k].id === id)
  if (idx) delete mockCells[idx]
  return Promise.resolve()
}

// ===================== BE-API-18 改课（换时段/日期） =====================
// 后端 POST /api/lessons/update 返回 Result<Void>，调用方需自行搬运本地单元格数据。
export function updateLesson(
  id: number,
  data: { slotId: number; lessonDate: string }
): Promise<void> {
  if (!USE_MOCK) {
    return request.post<void, void>('/lessons/update', { id, ...data })
  }
  const srcKey = Object.keys(mockCells).find((k) => mockCells[k].id === id)
  if (!srcKey) return Promise.reject(new Error('排课不存在'))
  const targetKey = cellKey(data.lessonDate, data.slotId)
  if (targetKey !== srcKey && mockCells[targetKey]) {
    const err: { message: string; response: { status: number; data: { message: string } } } = {
      message: '该时段已被占用（每格仅 1 人）',
      response: { status: 409, data: { message: '该时段已被占用（每格仅 1 人）' } }
    }
    return Promise.reject(err)
  }
  const cell = mockCells[srcKey]
  delete mockCells[srcKey]
  cell.slotId = data.slotId
  cell.lessonDate = data.lessonDate
  mockCells[targetKey] = cell
  return Promise.resolve()
}

// ===================== BE-API-19 保存当月（关闭上月顺延） =====================
export function saveMonth(data: SaveMonthPayload): Promise<{ closedCount: number }> {
  if (!USE_MOCK) {
    return request.post<{ closedCount: number }, { closedCount: number }>('/lessons/save-month', data)
  }
  return Promise.resolve({ closedCount: 0 })
}

// ===================== BE-API-20 按学生出图数据 =====================
export function fetchStudentExport(
  studentId: number,
  params: { year: number; month: number }
): Promise<StudentExport> {
  if (!USE_MOCK) {
    return request
      .post<RawStudentExport, RawStudentExport>('/lessons/student-export', {
        studentId,
        ...params
      })
      .then((raw) => adaptStudentExport(studentId, params, raw))
  }
  const { year, month } = params
  const ym = `${year}-${String(month).padStart(2, '0')}`
  seedMonth(ym)
  const student = mockStudents.find((s) => s.id === studentId)
  if (!student) return Promise.reject(new Error('学生不存在'))
  const total = new Date(year, month, 0).getDate()
  const days: string[] = []
  for (let d = 1; d <= total; d++) days.push(`${ym}-${String(d).padStart(2, '0')}`)
  const cells: Record<string, LessonCell> = {}
  Object.entries(mockCells).forEach(([k, c]) => {
    if (c.studentId === studentId && c.lessonDate.startsWith(ym)) cells[k] = clone(c)
  })
  return Promise.resolve({ student: clone(student), days, slots: clone(mockTimeSlots), cells })
}

// ===================== BE-API-21 标记顺延（必填 absentBy + absentReason） =====================
// 后端 POST /lessons/absent 返回 Result<Void>（置 status=ABSENT、closed=0），
// 调用方按入参在本地补状态，不要读返回值。
export function markAbsent(
  id: number,
  data: { absentBy: AbsentBy; absentReason: string }
): Promise<void> {
  if (!USE_MOCK) return request.post<void, void>('/lessons/absent', { id, ...data })
  const key = Object.keys(mockCells).find((k) => mockCells[k].id === id)
  if (key) {
    mockCells[key].status = 'ABSENT'
    mockCells[key].absentBy = data.absentBy
    mockCells[key].absentReason = data.absentReason
    mockCells[key].closed = false
  }
  return Promise.resolve()
}

// ===================== BE-API-22 状态切换 =====================
// 后端 POST /lessons/status 返回 Result<Void>，调用方按入参在本地补状态。
export function changeLessonStatus(id: number, data: { status: LessonStatus }): Promise<void> {
  if (!USE_MOCK) return request.post<void, void>('/lessons/status', { id, ...data })
  const key = Object.keys(mockCells).find((k) => mockCells[k].id === id)
  if (key) mockCells[key].status = data.status
  return Promise.resolve()
}

// ===================== 今日日期工具 =====================
function todayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 今日待补课 mock（makeUpDate 指向今天；status=ABSENT，closed=0）。
// 关闭动作走 makeUp.closeMakeUp（BE-API-25）；此处仅用于今日视图展示。
const mockPendingToday: LessonCell[] = [
  { id: 301, studentId: 1, studentName: '王小明', courseId: 0, slotId: 3, slotLabel: '14:00-15:30', lessonDate: '2026-08-20', status: 'ABSENT', absentBy: 'student', absentReason: '学生病假', makeUpDate: '', closed: false, remark: '' },
  { id: 302, studentId: 3, studentName: '张小红', courseId: 0, slotId: 1, slotLabel: '09:00-10:30', lessonDate: '2026-09-10', status: 'ABSENT', absentBy: 'teacher', absentReason: '老师请假', makeUpDate: '', closed: false, remark: '' }
]

// ===================== BE-API-33 今日视图 =====================
export function fetchToday(): Promise<{ lessons: LessonCell[]; pendingToday: LessonCell[] }> {
  if (!USE_MOCK) {
    return request.post<{ lessons: LessonCell[]; pendingToday: LessonCell[] }, { lessons: LessonCell[]; pendingToday: LessonCell[] }>(
      '/lessons/today'
    )
  }
  const t = todayStr()
  const seedToday = (id: number, studentId: number, slotId: number, status: LessonStatus): void => {
    const key = cellKey(t, slotId)
    if (mockCells[key]) return
    const stu = mockStudents.find((s) => s.id === studentId)
    const sl = mockTimeSlots.find((s) => s.id === slotId)
    if (!stu || !sl) return
    mockCells[key] = {
      id,
      studentId,
      studentName: stu.name,
      courseId: 0,
      slotId,
      slotLabel: slotLabel(sl),
      lessonDate: t,
      status,
      absentBy: null,
      absentReason: null,
      makeUpDate: null,
      closed: false,
      remark: ''
    }
  }
  seedToday(201, 2, 1, 'NORMAL')
  seedToday(202, 1, 3, 'UNTAKEN')
  seedToday(203, 3, 1, 'ABSENT')
  const lessons: LessonCell[] = Object.values(mockCells)
    .filter((c) => c.lessonDate === t)
    .sort((a, b) => a.slotId - b.slotId)
  const pendingToday = clone(mockPendingToday).map((p) => ({ ...p, makeUpDate: t }))
  return Promise.resolve({ lessons, pendingToday })
}

// ===================== BE-API-34 导出当月排课表 Excel =====================
// 后端 EasyExcel 生成 xlsx 二进制流；前端只负责触发下载。
// 注意：后端异常时返回的是 JSON（Result 包装），此时按 blob 收到需转文本解析 message。
export function exportMonthExcel(data: { year: number; month: number }): Promise<Blob> {
  return request.post<Blob, Blob>('/lessons/export', data, { responseType: 'blob' })
}
