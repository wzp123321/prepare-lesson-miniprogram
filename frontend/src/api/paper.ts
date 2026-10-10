// 试卷 —— BE-P-01~10（/api/papers/*，统一 POST + Result 包装）
import request from '@/utils/request'
import type { Paper, PaperDetail, PaperType, Question } from '@/types'

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

// BE-P-11 一键复用：以蓝本卷生成一份新的可编辑卷（复制编排+卷内快照，不克隆题库行）
export function reusePaper(data: {
  sourceId: number
  title?: string
  studentId?: number
}): Promise<number> {
  return request.post<number, number>('/papers/reuse', data)
}

/** 卷面编排项：edited=true 时 override 里的内容会写入试卷内容覆盖表（改卷不动题库） */
export interface PaperQuestionItem {
  questionId: number
  edited?: boolean
  /** 卷内编辑内容（仅 edited=true 时有意义） */
  qtype?: string
  stem?: string
  options?: string | null
  answer?: string | null
  analysis?: string | null
  difficulty?: number
}

// BE-P-07 编排题目（整体覆盖，数组顺序即题号顺序；items 可携带卷内编辑内容）
export function setPaperQuestions(data: {
  paperId: number
  items: PaperQuestionItem[]
}): Promise<void> {
  return request.post<void, void>('/papers/questions/set', data)
}

// BE-P-08 一键组卷（按知识点随机抽题，直接落库）
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

/** 组卷入参：题量可按知识点分别设量，也可统一 countPerKp */
export interface PaperGenerateParams {
  title?: string
  grade?: string
  kpIds: number[]
  /** 按知识点分别设量，优先级高于 countPerKp */
  kpCounts?: { kpId: number; count: number }[]
  countPerKp?: number
  /** 整卷题量上限，空 = 不限 */
  maxTotal?: number
  difficulty?: number
  paperType?: PaperType
  studentId?: number
}

/** 组卷试抽结果：题目 + 每个知识点的抽题明细 */
export interface PaperGeneratePreview {
  questions: Question[]
  total: number
  picks: {
    kpId: number
    kpName: string | null
    /** 期望抽几题 */
    wanted: number
    /** 题库符合条件的总量 */
    available: number
    /** 实际抽到几题 */
    picked: number
  }[]
}

// BE-P-09 组卷试抽（不落库，供预览与逐题替换）
export function previewGeneratePaper(data: PaperGenerateParams): Promise<PaperGeneratePreview> {
  return request.post<PaperGeneratePreview, PaperGeneratePreview>('/papers/generate/preview', data)
}

// BE-P-10 组卷落库（按确认后的题目顺序建卷）
export function commitGeneratePaper(data: {
  title?: string
  grade?: string
  paperType?: PaperType
  studentId?: number
  remark?: string
  questionIds: number[]
}): Promise<number> {
  return request.post<number, number>('/papers/generate/commit', data)
}
