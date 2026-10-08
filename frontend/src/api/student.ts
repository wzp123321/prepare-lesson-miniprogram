// S01 学生管理 —— BE-API-01~05
// 真实路径走 /api/students；联调前 USE_MOCK=true 走本地内存数据（src/api/mockData.ts）。
// TODO(W5-03): 后端 Wave A 就绪后将 USE_MOCK 改为 false。
import request from '@/utils/request'
import type { Student, StudentStatus, PageResult } from '@/types'
import { mockStudents, autoColor } from './mockData'

const USE_MOCK = false

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T
let studentSeq = 1000

export interface StudentQuery {
  keyword?: string
  grade?: string
  status?: number
  page?: number
  size?: number
}

// BE-API-01 列表+搜索（?keyword=&grade=&status=&page=&size=）
export function fetchStudents(params: StudentQuery): Promise<PageResult<Student>> {
  if (!USE_MOCK) {
    return request.get<PageResult<Student>, PageResult<Student>>('/students', { params })
  }
  const { keyword, grade, status, page = 1, size = 10 } = params
  let list = clone(mockStudents)
  if (keyword && keyword.trim()) {
    const k = keyword.trim()
    list = list.filter((s) => s.name.includes(k) || s.phone.includes(k))
  }
  if (grade) list = list.filter((s) => s.grade === grade)
  if (typeof status === 'number') list = list.filter((s) => s.status === status)
  const total = list.length
  const start = (page - 1) * size
  return Promise.resolve({ list: list.slice(start, start + size), total, page, size })
}

// BE-API-02 详情（含 pendingMakeUpCount）
export function fetchStudentDetail(id: number): Promise<Student & { pendingMakeUpCount: number }> {
  if (!USE_MOCK) {
    return request.get<Student & { pendingMakeUpCount: number }, Student & { pendingMakeUpCount: number }>(
      `/students/${id}`
    )
  }
  const s = mockStudents.find((x) => x.id === id)
  if (!s) return Promise.reject(new Error('学生不存在'))
  return Promise.resolve({ ...clone(s), pendingMakeUpCount: 0 })
}

// BE-API-03 新增（body: name,grade,phone,parentWechat,address,price,color,remark）
export function createStudent(data: Partial<Student>): Promise<Student> {
  if (!USE_MOCK) return request.post<Student, Student>('/students', data)
  const id = ++studentSeq
  const color = data.color || autoColor(mockStudents.map((s) => s.color))
  const now = '2026-01-01 00:00:00'
  const student: Student = {
    id,
    name: data.name || '',
    grade: data.grade || '',
    phone: data.phone || '',
    parentWechat: data.parentWechat || '',
    address: data.address || '',
    price: data.price ?? 0,
    status: (data.status ?? 1) as StudentStatus,
    color,
    remark: data.remark || '',
    createTime: now,
    updateTime: now
  }
  mockStudents.push(student)
  return Promise.resolve(clone(student))
}

// BE-API-04 修改
export function updateStudent(id: number, data: Partial<Student>): Promise<Student> {
  if (!USE_MOCK) return request.put<Student, Student>(`/students/${id}`, data)
  const s = mockStudents.find((x) => x.id === id)
  if (!s) return Promise.reject(new Error('学生不存在'))
  Object.assign(s, data, { updateTime: '2026-01-01 00:00:00' })
  return Promise.resolve(clone(s))
}

// BE-API-05 删除（删除保护：存在未结课程或未来排课时返回 409）
export function deleteStudent(id: number): Promise<void> {
  if (!USE_MOCK) return request.delete<void, void>(`/students/${id}`)
  const idx = mockStudents.findIndex((x) => x.id === id)
  if (idx === -1) return Promise.reject(new Error('学生不存在'))
  // mock 中以「在读」状态模拟存在未结课程/未来排课 → 触发删除保护
  if (mockStudents[idx].status === 1) {
    const err: { message: string; response: { status: number; data: { message: string } } } = {
      message: '该学生存在未结课程或未来排课，请先改为暂停归档',
      response: {
        status: 409,
        data: { message: '该学生存在未结课程或未来排课，请先改为暂停归档' }
      }
    }
    return Promise.reject(err)
  }
  mockStudents.splice(idx, 1)
  return Promise.resolve()
}
