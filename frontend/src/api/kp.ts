// 知识点管理 —— BE-K-01~04（/api/kp/*，统一 POST + Result 包装）
import request from '@/utils/request'
import type { KnowledgePoint } from '@/types'

// BE-K-01 列表（按年级 / 大类 / 启用过滤，带题量）
export function fetchKps(
  params: { grade?: string; category?: string; enabled?: number } = {}
): Promise<KnowledgePoint[]> {
  return request.post<KnowledgePoint[], KnowledgePoint[]>('/kp/list', params)
}

// BE-K-02 新增
export function createKp(data: Partial<KnowledgePoint>): Promise<number> {
  return request.post<number, number>('/kp/create', data)
}

// BE-K-03 修改
export function updateKp(data: Partial<KnowledgePoint>): Promise<void> {
  return request.post<void, void>('/kp/update', data)
}

// BE-K-04 删除（被题目 / 课次引用时后端返回 409）
export function deleteKp(id: number): Promise<void> {
  return request.post<void, void>('/kp/delete', { id })
}
