// Wave 2 本地 mock 数据兜底层（仅前端演示 / 联调前可用）
//
// 后端 Wave A（BE-API-01~14、26~29）就绪前，各 api 模块以 USE_MOCK=true 走本文件内存数据，
// 保证列表/表单交互在沙箱内可完整演示。
// TODO(W5-03): 后端就绪后，将各 api 模块的 USE_MOCK 改为 false，走真实 /api 路径，本文件即可停用。

import type { Student, TimeSlot, Course, Dict } from '@/types'

/** 专属颜色调色板（新增学生自动分配用） */
export const COLOR_PALETTE = [
  '#409EFF',
  '#67C23A',
  '#E6A23C',
  '#F56C6C',
  '#909399',
  '#9254DE',
  '#13C2C2',
  '#EB2F96'
]

/** S07 年级字典（mock） */
export const mockGrades: Dict[] = [
  { id: 1, dictType: 'grade', dictValue: '一年级', sortOrder: 1, enabled: true },
  { id: 2, dictType: 'grade', dictValue: '二年级', sortOrder: 2, enabled: true },
  { id: 3, dictType: 'grade', dictValue: '三年级', sortOrder: 3, enabled: true },
  { id: 4, dictType: 'grade', dictValue: '四年级', sortOrder: 4, enabled: true },
  { id: 5, dictType: 'grade', dictValue: '五年级', sortOrder: 5, enabled: true },
  { id: 6, dictType: 'grade', dictValue: '六年级', sortOrder: 6, enabled: true }
]

/** S07 顺延原因字典（mock） */
export const mockAbsentReasons: Dict[] = [
  { id: 11, dictType: 'absent_reason', dictValue: '学生病假', sortOrder: 1, enabled: true },
  { id: 12, dictType: 'absent_reason', dictValue: '学生事假', sortOrder: 2, enabled: true },
  { id: 13, dictType: 'absent_reason', dictValue: '老师请假', sortOrder: 3, enabled: true },
  { id: 14, dictType: 'absent_reason', dictValue: '法定节假日', sortOrder: 4, enabled: true }
]

/** S01 学生（mock） */
export const mockStudents: Student[] = [
  {
    id: 1,
    name: '王小明',
    grade: '一年级',
    phone: '13800000001',
    parentWechat: 'wx_ming',
    address: '北京市朝阳区建国路1号',
    price: 200,
    status: 1,
    color: '#409EFF',
    remark: '周三、周五上课',
    createTime: '2026-01-01 00:00:00',
    updateTime: '2026-01-01 00:00:00'
  },
  {
    id: 2,
    name: '李华',
    grade: '二年级',
    phone: '13800000002',
    parentWechat: 'wx_lihua',
    address: '北京市海淀区中关村大街2号',
    price: 220,
    status: 1,
    color: '#67C23A',
    remark: '',
    createTime: '2026-01-01 00:00:00',
    updateTime: '2026-01-01 00:00:00'
  },
  {
    id: 3,
    name: '张小红',
    grade: '三年级',
    phone: '13800000003',
    parentWechat: 'wx_hong',
    address: '北京市西城区金融街3号',
    price: 180,
    status: 0,
    color: '#E6A23C',
    remark: '暂停中',
    createTime: '2026-01-01 00:00:00',
    updateTime: '2026-01-01 00:00:00'
  }
]

/** S02 时间段（mock） */
export const mockTimeSlots: TimeSlot[] = [
  { id: 1, startTime: '09:00:00', endTime: '10:30:00', sortOrder: 1, enabled: true },
  { id: 2, startTime: '10:30:00', endTime: '12:00:00', sortOrder: 2, enabled: true },
  { id: 3, startTime: '14:00:00', endTime: '15:30:00', sortOrder: 3, enabled: true },
  { id: 4, startTime: '15:30:00', endTime: '17:00:00', sortOrder: 4, enabled: false }
]

/** S03 课程（每生一门语文课，price 取自学生，mock） */
export const mockCourses: Course[] = mockStudents.map((s, i) => ({
  id: 100 + i,
  studentId: s.id,
  subject: '语文',
  price: s.price,
  remark: '',
  enabled: true
}))

/** 自动分配一个未被占用专属颜色 */
export function autoColor(used: string[]): string {
  const free = COLOR_PALETTE.find((c) => !used.includes(c))
  return free || COLOR_PALETTE[Math.floor(Math.random() * COLOR_PALETTE.length)]
}

/** 判断错误是否为 409 冲突（删除保护 / 重叠 / 重复建课） */
export function isConflict(e: unknown): boolean {
  return (e as { response?: { status?: number } })?.response?.status === 409
}

/** 提取错误信息文案（统一返回体或 Error.message） */
export function errMsg(e: unknown): string {
  const respMsg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
  const msg = (e as { message?: string })?.message
  return respMsg || msg || '操作失败'
}
