// 本地 mock 数据兜底层（仅联调前的演示用）
//
// 各 api 模块保留 USE_MOCK 分支指向本文件的内存数据；当前所有模块 USE_MOCK 均为 false，
// 走真实 /api 路径。若后端不可用需临时演示，可把对应模块的 USE_MOCK 置回 true。
//
// 注意：通用工具函数已迁出本文件（原寄居在此导致本文件无法退场）——
//   错误处理 errMsg / isConflict → utils/error.ts
//   颜色分配 autoColor            → utils/color.ts

import type { Student, TimeSlot, Course, Dict } from '@/types'

/** S07 年级字典（mock） */
export const mockGrades: Dict[] = [
  { id: 1, dictType: 'grade', dictValue: '一年级', sortOrder: 1, enabled: 1 },
  { id: 2, dictType: 'grade', dictValue: '二年级', sortOrder: 2, enabled: 1 },
  { id: 3, dictType: 'grade', dictValue: '三年级', sortOrder: 3, enabled: 1 },
  { id: 4, dictType: 'grade', dictValue: '四年级', sortOrder: 4, enabled: 1 },
  { id: 5, dictType: 'grade', dictValue: '五年级', sortOrder: 5, enabled: 1 },
  { id: 6, dictType: 'grade', dictValue: '六年级', sortOrder: 6, enabled: 1 }
]

/** S07 顺延原因字典（mock） */
export const mockAbsentReasons: Dict[] = [
  { id: 11, dictType: 'absent_reason', dictValue: '学生病假', sortOrder: 1, enabled: 1 },
  { id: 12, dictType: 'absent_reason', dictValue: '学生事假', sortOrder: 2, enabled: 1 },
  { id: 13, dictType: 'absent_reason', dictValue: '老师请假', sortOrder: 3, enabled: 1 },
  { id: 14, dictType: 'absent_reason', dictValue: '法定节假日', sortOrder: 4, enabled: 1 }
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
  { id: 1, startTime: '09:00:00', endTime: '10:30:00', sortOrder: 1, enabled: 1 },
  { id: 2, startTime: '10:30:00', endTime: '12:00:00', sortOrder: 2, enabled: 1 },
  { id: 3, startTime: '14:00:00', endTime: '15:30:00', sortOrder: 3, enabled: 1 },
  { id: 4, startTime: '15:30:00', endTime: '17:00:00', sortOrder: 4, enabled: 0 }
]

/** S03 课程（每生一门语文课，price 取自学生，mock） */
export const mockCourses: Course[] = mockStudents.map((s, i) => ({
  id: 100 + i,
  studentId: s.id,
  subject: '语文',
  price: s.price,
  remark: '',
  enabled: 1
}))
