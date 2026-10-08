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
  MADEUP: { label: '已补', bg: '#eef2ff', fg: '#6366f1', tagType: 'primary' },
  CANCELLED: { label: '作废', bg: '#f4f4f5', fg: '#909399', tagType: 'warning' }
}

export const ABSENT_BY_LABEL: Record<AbsentBy, string> = {
  student: '学生请假',
  teacher: '老师请假'
}

/**
 * 合法状态迁移（对齐后端 LessonServiceImpl.isAllowedTransition）：
 * UNTAKEN → NORMAL（走 /lessons/status）/ ABSENT（走 /lessons/absent）
 * ABSENT  → MADEUP（走 /lessons/make-up）/ CANCELLED（走 /lessons/status）
 * NORMAL / MADEUP / CANCELLED 为终态，无合法迁移目标。
 * 前端据此过滤选项，避免提交后被后端 400 拒绝。
 */
export function allowedNextStatuses(cur: LessonStatus | null | undefined): LessonStatus[] {
  if (cur === 'UNTAKEN') return ['NORMAL', 'ABSENT']
  if (cur === 'ABSENT') return ['MADEUP', 'CANCELLED']
  return []
}

/** 状态操作弹窗的选项文案（比 STATUS_META.label 更贴合「改为…」的语境） */
export const STATUS_ACTION_LABEL: Record<LessonStatus, string> = {
  UNTAKEN: '未上',
  NORMAL: '正常上课',
  ABSENT: '顺延（请假）',
  MADEUP: '已补课',
  CANCELLED: '作废'
}
