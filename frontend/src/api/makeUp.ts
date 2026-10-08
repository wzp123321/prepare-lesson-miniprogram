// S06 补课闭环与待补课 —— BE-API-23~25
// USE_MOCK=true 兜底本地内存数据（无后端时演示 S06 待补列表 / 安排补课 / 手动关闭）；
// TODO(W5-03): 后端 Wave C 就绪后将 USE_MOCK 改为 false，走真实 /api 路径。
import request from '@/utils/request'
import type { LessonCell } from '@/types'

const USE_MOCK = false
const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v) as string) as T

// 待补 mock：status=ABSENT 且 closed=0（全局实时，跨学生）
const mockPending: LessonCell[] = [
  { id: 601, studentId: 1, studentName: '王小明', courseId: 0, slotId: 3, slotLabel: '14:00-15:30', lessonDate: '2026-08-20', status: 'ABSENT', absentBy: 'student', absentReason: '学生病假', makeUpDate: null, closed: false, remark: '' },
  { id: 602, studentId: 3, studentName: '张小红', courseId: 0, slotId: 1, slotLabel: '09:00-10:30', lessonDate: '2026-09-10', status: 'ABSENT', absentBy: 'teacher', absentReason: '老师请假', makeUpDate: null, closed: false, remark: '' },
  { id: 603, studentId: 2, studentName: '李华', courseId: 0, slotId: 1, slotLabel: '09:00-10:30', lessonDate: '2026-09-12', status: 'ABSENT', absentBy: 'student', absentReason: '学生事假', makeUpDate: null, closed: false, remark: '' }
]

// BE-API-23 待补列表（status=ABSENT AND closed=0，全局实时）
export function fetchPendingMakeUp(): Promise<LessonCell[]> {
  if (!USE_MOCK) return request.get<LessonCell[], LessonCell[]>('/make-up/pending')
  return Promise.resolve(clone(mockPending.filter((p) => !p.closed)))
}

// BE-API-24 安排补课（body makeUpDate，可跨月）
export function arrangeMakeUp(id: number, data: { makeUpDate: string }): Promise<void> {
  if (!USE_MOCK) return request.put<void, void>(`/lessons/${id}/make-up`, data)
  const p = mockPending.find((x) => x.id === id)
  if (p) p.makeUpDate = data.makeUpDate
  return Promise.resolve()
}

// BE-API-25 手动关闭（「已安排进本月课程」）—— 仅置 closed=1，status 保持 ABSENT
export function closeMakeUp(lessonId: number): Promise<void> {
  if (!USE_MOCK) return request.put<void, void>(`/make-up/${lessonId}/close`)
  const p = mockPending.find((x) => x.id === lessonId)
  if (p) p.closed = true
  return Promise.resolve()
}
