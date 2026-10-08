// 状态机统一来源（W5-04 全局收敛点）
// 与 ScheduleView 网格底色/标签完全一致，供 S04/S09/S10/S08 统一引用，避免各页重复定义导致不一致。
import type { LessonStatus, AbsentBy } from '@/types'

export interface StatusMeta {
  label: string
  bg: string
  fg: string
  tagType: 'success' | 'warning' | 'info' | 'danger' | 'primary'
}

export const STATUS_META: Record<LessonStatus, StatusMeta> = {
  UNTAKEN: { label: '未上', bg: '#ffffff', fg: '#606266', tagType: 'info' },
  NORMAL: { label: '正常上课', bg: '#f0f9eb', fg: '#67c23a', tagType: 'success' },
  ABSENT: { label: '顺延', bg: '#fef0f0', fg: '#f56c6c', tagType: 'danger' },
  MADEUP: { label: '已补', bg: '#ecf5ff', fg: '#409eff', tagType: 'primary' },
  CANCELLED: { label: '作废', bg: '#f4f4f5', fg: '#909399', tagType: 'warning' }
}

export const ABSENT_BY_LABEL: Record<AbsentBy, string> = {
  student: '学生请假',
  teacher: '老师请假'
}
