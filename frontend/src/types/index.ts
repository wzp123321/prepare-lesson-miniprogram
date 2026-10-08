// 全局实体类型骨架 —— 字段镜像后端概设 §2.3 数据字典
// 仅定义结构，不写业务逻辑；具体字段语义以后端为唯一真相源。

/** 学生状态：1=在读，0=暂停 */
export type StudentStatus = 1 | 0

/** 课程状态：启用/停用（enabled 字段） */
export interface Student {
  id: number
  name: string
  grade: string
  phone: string
  parentWechat: string
  address: string
  /** 单价（课程价格来源） */
  price: number
  status: StudentStatus
  /** 专属颜色，用于课表着色 */
  color: string
  remark: string
  createTime: string
  updateTime: string
}

/**
 * 时间段（后端 §2.3 time_slot）
 * 注：后端未定义 name 字段（前端概设 S02 标注「建议，待确认」），此处暂以 startTime~endTime 展示。
 */
export interface TimeSlot {
  id: number
  startTime: string
  endTime: string
  sortOrder: number
  enabled: boolean
  /** 待确认字段，后端确认后补全 */
  name?: string
}

/** 课程（每生一门语文课，后端 §2.3 course） */
export interface Course {
  id: number
  studentId: number
  subject: '语文'
  price: number
  remark: string
  enabled: boolean
}

/** 课程状态机（PRD 第 6 章） */
export type LessonStatus = 'UNTAKEN' | 'NORMAL' | 'ABSENT' | 'MADEUP' | 'CANCELLED'

/** 请假方（BE-API-21） */
export type AbsentBy = 'student' | 'teacher'

/** 排课/请假记录（后端 §2.3 lesson） */
export interface Lesson {
  id: number
  studentId: number
  courseId: number
  slotId: number
  lessonDate: string
  status: LessonStatus
  absentBy: AbsentBy | null
  absentReason: string | null
  makeUpDate: string | null
  /** 待补显示控制，与 status 正交 */
  closed: boolean
  remark: string
}

/**
 * 网格/今日/待补视图用的单元格（BE-API-15/23/33 返回）
 * 在 Lesson 基础上补充展示字段（学生名、时段等）
 */
export interface LessonCell {
  id: number
  studentId: number
  studentName: string
  courseId: number
  slotId: number
  slotLabel?: string
  lessonDate: string
  status: LessonStatus
  absentBy: AbsentBy | null
  absentReason: string | null
  makeUpDate: string | null
  closed: boolean
  remark: string
}

/** 字典项（后端 §2.3 dict，type=grade|absent_reason） */
export interface Dict {
  id: number
  dictType: string
  dictValue: string
  sortOrder: number
  enabled: boolean
}

/** 统一分页响应体 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/** 当月网格数据（BE-API-15） */
export interface MonthGrid {
  days: string[]
  slots: TimeSlot[]
  cells: Record<string, LessonCell> // key = `${date}#${slotId}`
}

/** 月总览统计（BE-API-30） */
export interface MonthStatistic {
  scheduled: number
  normal: number
  absent: number
  absentByStudent: number
  absentByTeacher: number
  madeUp: number
  cancelled: number
  pending: number
}

/** 统一返回体（AGENTS 4.2 / 前端概设 §2） */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}
