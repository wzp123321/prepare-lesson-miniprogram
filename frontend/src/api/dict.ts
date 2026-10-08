// S07 字典管理 —— BE-API-26~29（grade / absent_reason）
// 真实路径走 /api/dicts；联调前 USE_MOCK=true 走本地内存数据（src/api/mockData.ts）。
// TODO(W5-03): 后端 Wave A 就绪后将 USE_MOCK 改为 false。
import request from '@/utils/request'
import type { Dict } from '@/types'
import { mockGrades, mockAbsentReasons } from './mockData'

const USE_MOCK = false

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T
let dictSeq = 9000

function storeOf(type: string): Dict[] {
  return type === 'grade' ? mockGrades : mockAbsentReasons
}

// BE-API-26 列表（按 type=grade|absent_reason）
export function fetchDicts(type: string): Promise<Dict[]> {
  if (!USE_MOCK) return request.get<Dict[], Dict[]>('/dicts', { params: { type } })
  const list = storeOf(type)
    .filter((d) => d.dictType === type)
    .sort((a, b) => a.sortOrder - b.sortOrder)
  return Promise.resolve(clone(list))
}

// BE-API-27 新增（body: dictType,dictValue,sortOrder,enabled）
export function createDict(data: Partial<Dict>): Promise<Dict> {
  if (!USE_MOCK) return request.post<Dict, Dict>('/dicts', data)
  const dictType = data.dictType || 'grade'
  const id = ++dictSeq
  const d: Dict = {
    id,
    dictType,
    dictValue: data.dictValue || '',
    sortOrder: data.sortOrder ?? storeOf(dictType).length + 1,
    enabled: data.enabled ?? true
  }
  storeOf(dictType).push(d)
  return Promise.resolve(clone(d))
}

// BE-API-28 修改
export function updateDict(id: number, data: Partial<Dict>): Promise<Dict> {
  if (!USE_MOCK) return request.put<Dict, Dict>(`/dicts/${id}`, data)
  const d = [...mockGrades, ...mockAbsentReasons].find((x) => x.id === id)
  if (!d) return Promise.reject(new Error('字典项不存在'))
  Object.assign(d, data)
  return Promise.resolve(clone(d))
}

// BE-API-29 删除
export function deleteDict(id: number): Promise<void> {
  if (!USE_MOCK) return request.delete<void, void>(`/dicts/${id}`)
  const remove = (arr: Dict[]) => {
    const i = arr.findIndex((x) => x.id === id)
    if (i > -1) arr.splice(i, 1)
  }
  remove(mockGrades)
  remove(mockAbsentReasons)
  return Promise.resolve()
}
