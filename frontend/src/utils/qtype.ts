// 题型公用工具：卷面大题顺序表 + 题型读取
// 客户端已有多处按题型分组（打印页 / 制卷台），顺序表只在这里维护一份。
import { QTYPES } from '@/constants/question'
import type { Question } from '@/types'

/** 卷面大题顺序：与打印页、制卷台右侧构成统计一致 */
export const QTYPE_ORDER = ['选择', '填空', '判断', '阅读', '古诗文', '写作', '其他']

/** 取题型，缺失时归入「其他」 */
export function qtypeOf(q: Question | null | undefined): string {
  return q?.qtype || '其他'
}

/** 题型排序权重（未知题型排在标准题型之后） */
export function qtypeWeight(t: string): number {
  const i = QTYPE_ORDER.indexOf(t)
  return i < 0 ? QTYPE_ORDER.length : i
}

/**
 * 题型是否按「一大题下多小题并排」排版。
 * 阅读/古诗文 材料长、小题多，并排更省纸；其余题型一题一行。
 */
export function isPairedType(t: string): boolean {
  return t === '阅读' || t === '古诗文'
}

/** 所有标准题型（供筛选下拉用） */
export { QTYPES }
