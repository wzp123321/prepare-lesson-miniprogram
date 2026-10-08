// S08 数据总览 —— BE-API-30~32
// USE_MOCK=true 兜底本地内存数据（无后端时演示月总览 / 历史明细 / 月收入）；
// TODO(W5-03): 后端 Wave D 就绪后将 USE_MOCK 改为 false，走真实 /api 路径。
import request from '@/utils/request'
import type { MonthStatistic } from '@/types'

const USE_MOCK = false

// BE-API-30 月总览卡片（排 N/已上 X/顺延 Y 拆 学生a+老师b/已补 Z/作废 W/待补 K）
export function fetchMonthStatistic(params: { year: number; month: number }): Promise<MonthStatistic> {
  if (!USE_MOCK) return request.post<MonthStatistic, MonthStatistic>('/statistics/month', undefined, { params })
  return Promise.resolve({
    scheduled: 30,
    normal: 24,
    absent: 4,
    absentByStudent: 3,
    absentByTeacher: 1,
    madeUp: 1,
    cancelled: 1,
    pending: 3
  })
}

// BE-API-31 历史未上课明细（ABSENT 未补 + CANCELLED）
export interface MonthDetailRow {
  lessonDate: string
  studentName: string
  slot: string
  status: string
  absentBy: string
  absentReason: string
}
export function fetchMonthDetail(params: { year: number; month: number }): Promise<MonthDetailRow[]> {
  if (!USE_MOCK) {
    return request.post<MonthDetailRow[], MonthDetailRow[]>('/statistics/month/detail', undefined, { params })
  }
  return Promise.resolve([
    { lessonDate: '2026-09-05', studentName: '王小明', slot: '14:00-15:30', status: 'ABSENT', absentBy: 'student', absentReason: '学生病假' },
    { lessonDate: '2026-09-10', studentName: '张小红', slot: '09:00-10:30', status: 'CANCELLED', absentBy: '', absentReason: '法定节假日' }
  ])
}

// BE-API-32 月收入（口径 (NORMAL+MADEUP)×price）
export function fetchMonthIncome(params: { year: number; month: number }): Promise<{ income: number; formula: string }> {
  if (!USE_MOCK) {
    return request.post<{ income: number; formula: string }, { income: number; formula: string }>('/statistics/month/income', undefined, { params })
  }
  return Promise.resolve({ income: 5000, formula: '(NORMAL+MADEUP)×price' })
}
