// S03 课程管理 —— BE-API-11~14（每生一门语文课）
// 真实路径走 /api/students/{id}/course 与 /api/courses；联调前 USE_MOCK=true 走本地内存数据。
// TODO(W5-03): 后端 Wave A 就绪后将 USE_MOCK 改为 false。
import request from '@/utils/request'
import type { Course } from '@/types'
import { mockCourses, mockStudents } from './mockData'

const USE_MOCK = false

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T
let courseSeq = 5000

// BE-API-11 查该生语文课（无则返回 null）
export function fetchStudentCourse(studentId: number): Promise<Course | null> {
  if (!USE_MOCK) return request.get<Course | null, Course | null>(`/students/${studentId}/course`)
  const c = mockCourses.find((x) => x.studentId === studentId)
  return Promise.resolve(c ? clone(c) : null)
}

// BE-API-12 建（每生一门，body 仅 remark；price 自动取自 student）
export function createCourse(studentId: number, data: { remark?: string }): Promise<Course> {
  if (!USE_MOCK) return request.post<Course, Course>(`/students/${studentId}/course`, data)
  if (mockCourses.some((x) => x.studentId === studentId)) {
    const err: { message: string; response: { status: number; data: { message: string } } } = {
      message: '该生已建语文课',
      response: { status: 409, data: { message: '该生已建语文课' } }
    }
    return Promise.reject(err)
  }
  const price = mockStudents.find((s) => s.id === studentId)?.price ?? 0
  const course: Course = {
    id: ++courseSeq,
    studentId,
    subject: '语文',
    price,
    remark: data.remark || '',
    enabled: true
  }
  mockCourses.push(course)
  return Promise.resolve(clone(course))
}

// BE-API-13 修改（body: remark）
export function updateCourse(id: number, data: { remark?: string }): Promise<Course> {
  if (!USE_MOCK) return request.put<Course, Course>(`/courses/${id}`, data)
  const c = mockCourses.find((x) => x.id === id)
  if (!c) return Promise.reject(new Error('课程不存在'))
  Object.assign(c, data)
  return Promise.resolve(clone(c))
}

// BE-API-14 停用（enabled=0，保留历史）
export function disableCourse(id: number): Promise<void> {
  if (!USE_MOCK) return request.put<void, void>(`/courses/${id}/disable`)
  const c = mockCourses.find((x) => x.id === id)
  if (!c) return Promise.reject(new Error('课程不存在'))
  c.enabled = false
  return Promise.resolve()
}
