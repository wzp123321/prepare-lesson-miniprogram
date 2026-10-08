// S02 时间段管理 —— BE-API-06~10
// 真实路径走 /api/time-slots；联调前 USE_MOCK=true 走本地内存数据（src/api/mockData.ts）。
// TODO(W5-03): 后端 Wave A 就绪后将 USE_MOCK 改为 false。
import request from '@/utils/request'
import type { TimeSlot } from '@/types'
import { mockTimeSlots } from './mockData'

const USE_MOCK = false

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T
let slotSeq = 2000
// 已确认删除的 id（mock：首次被引用删除返回 409，确认后再删放行）
const confirmedDeletes = new Set<number>()

// BE-API-06 列表（按开始时间升序）
export function fetchTimeSlots(): Promise<TimeSlot[]> {
  if (!USE_MOCK) return request.post<TimeSlot[], TimeSlot[]>('/time-slots/list')
  const list = [...mockTimeSlots].sort((a, b) => a.startTime.localeCompare(b.startTime))
  return Promise.resolve(clone(list))
}

// BE-API-07 新增（body: name?,startTime,endTime,sortOrder,enabled）
export function createTimeSlot(data: Partial<TimeSlot>): Promise<TimeSlot> {
  if (!USE_MOCK) return request.post<TimeSlot, TimeSlot>('/time-slots/create', data)
  const id = ++slotSeq
  const ts: TimeSlot = {
    id,
    startTime: data.startTime || '00:00:00',
    endTime: data.endTime || '00:00:00',
    sortOrder: data.sortOrder ?? mockTimeSlots.length + 1,
    enabled: data.enabled ?? 1,
    name: data.name
  }
  mockTimeSlots.push(ts)
  return Promise.resolve(clone(ts))
}

// BE-API-08 修改
export function updateTimeSlot(id: number, data: Partial<TimeSlot>): Promise<TimeSlot> {
  if (!USE_MOCK) return request.post<TimeSlot, TimeSlot>('/time-slots/update', { ...data, id })
  const t = mockTimeSlots.find((x) => x.id === id)
  if (!t) return Promise.reject(new Error('时间段不存在'))
  Object.assign(t, data)
  return Promise.resolve(clone(t))
}

// BE-API-09 删除（有排课引用返回 409 + 引用计数，前端弹确认）
export function deleteTimeSlot(id: number): Promise<void> {
  if (!USE_MOCK) return request.post<void, void>('/time-slots/delete', { id })
  const idx = mockTimeSlots.findIndex((x) => x.id === id)
  if (idx === -1) return Promise.reject(new Error('时间段不存在'))
  // mock 中以「启用」状态模拟被排课引用；首次删除弹确认，确认后再删放行
  if (mockTimeSlots[idx].enabled && !confirmedDeletes.has(id)) {
    confirmedDeletes.add(id)
    const err: { message: string; response: { status: number; data: { message: string; referenceCount: number } } } = {
      message: '该时间段已被排课引用，确认删除？',
      response: {
        status: 409,
        data: { message: '该时间段已被排课引用', referenceCount: 3 }
      }
    }
    return Promise.reject(err)
  }
  mockTimeSlots.splice(idx, 1)
  confirmedDeletes.delete(id)
  return Promise.resolve()
}
