// 备课（课次 ↔ 知识点 / 试卷）—— BE-L-01~04（/api/prep/*，统一 POST + Result 包装）
import request from '@/utils/request'
import type { LessonPrep, Question, TodoPrep } from '@/types'

// BE-L-01 待备课列表
export function fetchTodoPrep(params: { from: string; to: string }): Promise<TodoPrep[]> {
  return request.post<TodoPrep[], TodoPrep[]>('/prep/todo', params)
}

// BE-L-02 单节课备课详情
export function fetchLessonPrep(lessonId: number): Promise<LessonPrep> {
  return request.post<LessonPrep, LessonPrep>('/prep/get', { lessonId })
}

// BE-L-03 保存备课安排（整体覆盖）
export function saveLessonPrep(data: {
  lessonId: number
  kpIds: number[]
  paperIds: number[]
  remark?: string
}): Promise<void> {
  return request.post<void, void>('/prep/save', data)
}

// BE-L-04 按已选知识点推荐可用题目
export function fetchSuggestQuestions(lessonId: number): Promise<Question[]> {
  return request.post<Question[], Question[]>('/prep/suggest', { lessonId })
}
