// S04 排课系统 —— BE-API-15~20（W3-01 / W3-03~06）
//
// 真实路径走 /api/lessons；联调前 USE_MOCK=true 走本地内存数据（见下方 mock 段），
// 与 student.ts / timeSlot.ts 的约定一致，保证无后端时网格可完整演示。
// TODO(W5-03): 后端 Wave B（BE-API-15~20）就绪后将 USE_MOCK 改为 false。
import request from '@/utils/request'
import type { MonthGrid, Lesson, LessonCell, LessonStatus, AbsentBy, TimeSlot, Student } from '@/types'
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
    return request.get<MonthGrid, MonthGrid>('/lessons/month', { params })
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
export function createLesson(data: {
  studentId: number
  slotId: number
  lessonDate: string
}): Promise<Lesson> {
  if (!USE_MOCK) {
    return request.post<Lesson, Lesson>('/lessons', data)
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
  return Promise.resolve(clone(lesson))
}

// ===================== BE-API-17 删课（拖出/点删） =====================
export function deleteLesson(id: number): Promise<void> {
  if (!USE_MOCK) {
    return request.delete<void, void>(`/lessons/${id}`)
  }
  const idx = Object.keys(mockCells).find((k) => mockCells[k].id === id)
  if (idx) delete mockCells[idx]
  return Promise.resolve()
}

// ===================== BE-API-18 改课（换时段/日期） =====================
export function updateLesson(
  id: number,
  data: { slotId: number; lessonDate: string }
): Promise<Lesson> {
  if (!USE_MOCK) {
    return request.put<Lesson, Lesson>(`/lessons/${id}`, data)
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
  return Promise.resolve(
    clone({
      id: cell.id,
      studentId: cell.studentId,
      courseId: cell.courseId,
      slotId: cell.slotId,
      lessonDate: cell.lessonDate,
      status: cell.status,
      absentBy: cell.absentBy,
      absentReason: cell.absentReason,
      makeUpDate: cell.makeUpDate,
      closed: cell.closed,
      remark: cell.remark
    })
  )
}

// ===================== BE-API-19 保存当月（关闭上月顺延） =====================
export function saveMonth(data: SaveMonthPayload): Promise<{ closedCount: number }> {
  if (!USE_MOCK) {
    return request.post<{ closedCount: number }, { closedCount: number }>('/lessons/save-month', data)
  }
  // mock：模拟「关闭上月顺延」已完成，返回 0（真实后端按上月 ABSENT&closed=0 计数）
  return Promise.resolve({ closedCount: 0 })
}

// ===================== BE-API-20 按学生出图数据 =====================
export function fetchStudentExport(
  studentId: number,
  params: { year: number; month: number }
): Promise<StudentExport> {
  if (!USE_MOCK) {
    return request.get<StudentExport, StudentExport>(`/lessons/student/${studentId}/export`, { params })
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
// TODO(W4-02)
export function markAbsent(
  id: number,
  data: { absentBy: AbsentBy; absentReason: string }
): Promise<Lesson> {
  if (!USE_MOCK) return request.put<Lesson, Lesson>(`/lessons/${id}/absent`, data)
  const key = Object.keys(mockCells).find((k) => mockCells[k].id === id)
  if (key) {
    mockCells[key].status = 'ABSENT'
    mockCells[key].absentBy = data.absentBy
    mockCells[key].absentReason = data.absentReason
    mockCells[key].closed = false
  }
  return Promise.resolve(
    clone({
      id,
      studentId: key ? mockCells[key].studentId : 0,
      courseId: 0,
      slotId: key ? mockCells[key].slotId : 0,
      lessonDate: key ? mockCells[key].lessonDate : '',
      status: 'ABSENT' as LessonStatus,
      absentBy: data.absentBy,
      absentReason: data.absentReason,
      makeUpDate: null,
      closed: false,
      remark: ''
    })
  )
}

// ===================== BE-API-22 状态切换 =====================
// TODO(W4-02)
export function changeLessonStatus(id: number, data: { status: LessonStatus }): Promise<Lesson> {
  if (!USE_MOCK) return request.put<Lesson, Lesson>(`/lessons/${id}/status`, data)
  const key = Object.keys(mockCells).find((k) => mockCells[k].id === id)
  if (key) mockCells[key].status = data.status
  return Promise.resolve(
    clone({
      id,
      studentId: key ? mockCells[key].studentId : 0,
      courseId: 0,
      slotId: key ? mockCells[key].slotId : 0,
      lessonDate: key ? mockCells[key].lessonDate : '',
      status: data.status,
      absentBy: null,
      absentReason: null,
      makeUpDate: null,
      closed: false,
      remark: ''
    })
  )
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
    return request.get<{ lessons: LessonCell[]; pendingToday: LessonCell[] }, { lessons: LessonCell[]; pendingToday: LessonCell[] }>(
      '/lessons/today'
    )
  }
  // 将今日课程写入 mockCells，便于标记接口（markAbsent/changeLessonStatus）按 id 命中
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
