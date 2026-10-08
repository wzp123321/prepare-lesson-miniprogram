// 卷子原文 → 结构化题目
//
// 用途：把「逐题录入」变成「粘贴整卷 + 自动切分」。
// 这里只做纯文本规则切分（不联网、不依赖 AI），切完之后可以再交给 AI 补答案 / 解析。
// 设计取舍：宁少切不多切 —— 切错了要人工合并，切碎了更麻烦。

export interface ParsedQuestion {
  /** 题干（多行用 \n 保留） */
  stem: string
  /** 题型：选择 / 填空 / 判断 / 阅读 / 古诗文 / 写作 / 其他 */
  qtype: string
  /** 选择题选项（形如 ["A. 张三", "B. 李四"]），非选择题为空数组 */
  options: string[]
  /** 答案：原文带了就带出来，没有则留空，等 AI 或手工补 */
  answer: string
  /** 解析（讲题要点） */
  analysis: string
  /** 难度 1 易 / 2 中 / 3 难 */
  difficulty: number
}

/**
 * 阿拉伯题号：`1.` `1、` `1)` `第1、`
 * 刻意**不接受**括号包裹的 `（1）`：语文卷里这多半是大题下的小问，拆开会把大题引文和小问割裂。
 */
const NUM_NO = /^\s*(?:第\s*)?(\d{1,3})\s*[.、．:：)）]\s*/
/** 中文题号：`一、` `二．` */
const CN_NO = /^\s*[一二三四五六七八九十]{1,3}\s*[、.．]\s*/
/** 选项行：`A. xxx` `A、xxx` `A．xxx` `A) xxx` */
const OPTION_LINE = /^\s*([A-Ha-h])\s*[.、．)）:：]\s*(.+)$/
/** 答案行：`答案：B` `【答案】B` `参考答案：…` */
const ANSWER_LINE = /^\s*[【[]?\s*(?:参考答案|标准答案|答案)\s*[】\]]?\s*[:：]?\s*(.*)$/
/** 解析行：`解析：…` `【解析】…` `点拨：…` */
const ANALYSIS_LINE = /^\s*[【[]?\s*(?:解析|分析|点拨|说明|考点)\s*[】\]]?\s*[:：]?\s*(.*)$/

type LineKind = 'start' | 'heading' | 'content'

/**
 * 判断一行的角色：
 *  - start：新题起点（`1.` `2、` 或内容够长的中文序号）
 *  - heading：分组标题（`一、基础题` 这类短标题），丢弃、不当题目
 *  - content：题目内容
 */
function classifyLine(line: string): LineKind {
  const t = line.trim()
  if (!t) return 'content'
  // 选项行（A. / B、）不是题号
  if (OPTION_LINE.test(t)) return 'content'
  if (NUM_NO.test(line)) return 'start'
  if (CN_NO.test(line)) {
    const body = stripNo(line).trim()
    // 短的、不带标点的中文序号行多是分组标题（一、基础题 / 二、阅读）
    if (body.length <= 8 && !/[。？?！!；;，,]/.test(body)) return 'heading'
    return 'start'
  }
  return 'content'
}

/** 去掉行首题号 */
function stripNo(line: string): string {
  return line.replace(NUM_NO, '').replace(CN_NO, '')
}

/** 猜测题型（先看有没有选项，再看题干特征词） */
export function guessQtype(stem: string, options: string[]): string {
  if (options.length >= 2) return '选择'
  if (/判断|对错|打[√×✓✗]|是否正确/.test(stem)) return '判断'
  if (/[（(]\s*[）)]|_{2,}|＿{2,}|……/.test(stem)) return '填空'
  if (/古诗|诗句|默写|文言|解释加点|翻译/.test(stem)) return '古诗文'
  if (/阅读|短文|文章|文段|选文|语段/.test(stem)) return '阅读'
  if (/作文|写作|写一段|不少于\s*\d+\s*字|拟写/.test(stem)) return '写作'
  return '其他'
}

/** 一个块 → 一道题 */
function buildQuestion(lines: string[]): ParsedQuestion {
  const stemParts: string[] = []
  const options: string[] = []
  let answer = ''
  let analysis = ''

  for (const line of lines) {
    const t = line.trim()
    if (!t) {
      if (stemParts.length) stemParts.push('')
      continue
    }
    const om = t.match(OPTION_LINE)
    if (om) {
      options.push(`${om[1].toUpperCase()}. ${om[2].trim()}`)
      continue
    }
    const am = t.match(ANSWER_LINE)
    if (am) {
      answer = am[1].trim()
      continue
    }
    const xm = t.match(ANALYSIS_LINE)
    if (xm) {
      analysis = xm[1].trim()
      continue
    }
    stemParts.push(t)
  }

  const stem = stemParts.join('\n').replace(/\n{3,}/g, '\n\n').trim()
  return {
    stem,
    options,
    answer,
    analysis,
    qtype: guessQtype(stem, options),
    difficulty: 2
  }
}

/**
 * 把整段卷子文字切成题目数组。
 * 没有任何题号时，整段作为一道题返回（方便只粘一道题的场景）。
 */
export function parseQuestions(raw: string): ParsedQuestion[] {
  const text = (raw || '').replace(/\r\n?/g, '\n').trim()
  if (!text) return []

  const blocks: string[][] = []
  let cur: string[] | null = null

  for (const line of text.split('\n')) {
    const kind = classifyLine(line)
    if (kind === 'start') {
      if (cur) blocks.push(cur)
      cur = [stripNo(line)]
    } else if (kind === 'heading') {
      // 分组标题：结束上一题，自己不作题目
      if (cur) blocks.push(cur)
      cur = null
    } else {
      if (!cur) {
        if (!line.trim()) continue // 题目之间的空行不另起块
        cur = []
      }
      cur.push(line)
    }
  }
  if (cur) blocks.push(cur)

  return blocks.map(buildQuestion).filter((q) => q.stem.length > 0)
}
