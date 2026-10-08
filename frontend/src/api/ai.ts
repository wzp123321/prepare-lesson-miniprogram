// 智能排课 —— Spring AI（DeepSeek），路径 /api/ai/*
// 两阶段：chat 只出方案（不落库）→ 老师确认 → apply 执行
import request from '@/utils/request'
import type {
  AiAction,
  AiApplyResult,
  AiChatResult,
  AiHistoryItem,
  AiParsedQuestion,
  AiSelection
} from '@/types'

// 对话：把自然语言指令翻译成排课方案
// history 用于多轮追问；selection 是老师对上一轮反问的点选结果（点选式引导）
export function chatSchedule(data: {
  message: string
  year?: number
  month?: number
  history?: AiHistoryItem[]
  selection?: AiSelection
}): Promise<AiChatResult> {
  return request.post<AiChatResult, AiChatResult>('/ai/schedule/chat', data)
}

// 确认执行方案
export function applySchedule(actions: AiAction[]): Promise<AiApplyResult> {
  return request.post<AiApplyResult, AiApplyResult>('/ai/schedule/apply', { actions })
}

// 题库批量导入用：把切分好的题目原文交给 AI，补答案 / 解析 / 难度 / 知识点
// 未配 API Key 时后端返回 500，调用方据此降级为「纯规则导入」
export function parseQuestionsByAi(data: {
  items: string[]
  grade?: string
  source?: string
  kpOptions?: { id: number; name: string }[]
}): Promise<AiParsedQuestion[]> {
  return request.post<AiParsedQuestion[], AiParsedQuestion[]>('/ai/parse-questions', data)
}
