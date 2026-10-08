// 题库 —— BE-Q-01~05（/api/questions/*，统一 POST + Result 包装）
import request from '@/utils/request'
import type { PageResult, Question } from '@/types'

export interface QuestionQuery {
  grade?: string
  kpId?: number
  qtype?: string
  /** 仅匹配题干 */
  keyword?: string
  studentId?: number
  /** true = 只看通用题 */
  onlyCommon?: boolean
  page?: number
  size?: number
}

// BE-Q-01 分页列表
export function fetchQuestions(params: QuestionQuery): Promise<PageResult<Question>> {
  return request.post<PageResult<Question>, PageResult<Question>>('/questions/list', params)
}

// BE-Q-02 详情
export function fetchQuestion(id: number): Promise<Question> {
  return request.post<Question, Question>('/questions/get', { id })
}

// BE-Q-03 录题
export function createQuestion(data: Partial<Question>): Promise<number> {
  return request.post<number, number>('/questions/create', data)
}

// BE-Q-04 改题
export function updateQuestion(data: Partial<Question>): Promise<void> {
  return request.post<void, void>('/questions/update', data)
}

// BE-Q-05 删题（被卷引用时后端返回 409）
export function deleteQuestion(id: number): Promise<void> {
  return request.post<void, void>('/questions/delete', { id })
}

// 批量入库（粘贴整卷导入用），返回成功入库条数
export function batchCreateQuestions(questions: Partial<Question>[]): Promise<number> {
  return request.post<number, number>('/questions/batch', { questions })
}
