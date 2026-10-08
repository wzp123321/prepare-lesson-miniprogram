// 试卷 —— BE-P-01~08（/api/papers/*，统一 POST + Result 包装）
import request from '@/utils/request'
import type { Paper, PaperDetail, PaperType } from '@/types'

export interface PaperQuery {
  grade?: string
  studentId?: number
  paperType?: PaperType
  keyword?: string
}

// BE-P-01 列表（带题数）
export function fetchPapers(params: PaperQuery = {}): Promise<Paper[]> {
  return request.post<Paper[], Paper[]>('/papers/list', params)
}

// BE-P-02 详情（含题目，按题号）
export function fetchPaperDetail(id: number): Promise<PaperDetail> {
  return request.post<PaperDetail, PaperDetail>('/papers/get', { id })
}

// BE-P-03 建卷
export function createPaper(data: Partial<Paper>): Promise<number> {
  return request.post<number, number>('/papers/create', data)
}

// BE-P-04 改卷
export function updatePaper(data: Partial<Paper>): Promise<void> {
  return request.post<void, void>('/papers/update', data)
}

// BE-P-05 删卷（编排与课次关联由后端一并清理，题目保留题库）
export function deletePaper(id: number): Promise<void> {
  return request.post<void, void>('/papers/delete', { id })
}

// BE-P-06 派生：克隆卷 + 克隆题目（改后不影响原卷）
export function clonePaper(data: { id: number; studentId?: number }): Promise<number> {
  return request.post<number, number>('/papers/clone', data)
}

// BE-P-07 编排题目（整体覆盖，数组顺序即题号顺序）
export function setPaperQuestions(data: { paperId: number; questionIds: number[] }): Promise<void> {
  return request.post<void, void>('/papers/questions/set', data)
}

// BE-P-08 一键组卷（按知识点随机抽题）
export function generatePaper(data: {
  title?: string
  grade?: string
  kpIds: number[]
  countPerKp: number
  difficulty?: number
  paperType?: PaperType
  studentId?: number
}): Promise<number> {
  return request.post<number, number>('/papers/generate', data)
}
