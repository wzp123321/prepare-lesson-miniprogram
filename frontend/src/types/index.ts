// 全局实体类型骨架 —— 字段镜像后端概设 §2.3 数据字典
// 仅定义结构，不写业务逻辑；具体字段语义以后端为唯一真相源。

/** 学生状态：1=在读，0=暂停 */
export type StudentStatus = 1 | 0

/** 课程状态：启用/停用（enabled 字段） */
export interface Student {
  id: number
  name: string
  grade: string
  phone: string
  parentWechat: string
  address: string
  /** 单价（课程价格来源） */
  price: number
  status: StudentStatus
  /** 专属颜色，用于课表着色 */
  color: string
  remark: string
  createTime: string
  updateTime: string
}

/**
 * 时间段（后端 §2.3 time_slot）
 * 注：后端未定义 name 字段（前端概设 S02 标注「建议，待确认」），此处暂以 startTime~endTime 展示。
 */
export interface TimeSlot {
  id: number
  startTime: string
  endTime: string
  sortOrder: number
  enabled: 0 | 1
  /** 待确认字段，后端确认后补全 */
  name?: string
}

/** 课程（每生一门语文课，后端 §2.3 course） */
export interface Course {
  id: number
  studentId: number
  subject: '语文'
  price: number
  remark: string
  enabled: 0 | 1
}

/** 课程状态机（PRD 第 6 章） */
export type LessonStatus = 'UNTAKEN' | 'NORMAL' | 'ABSENT' | 'MADEUP' | 'CANCELLED'

/** 请假方（BE-API-21） */
export type AbsentBy = 'student' | 'teacher'

/** 排课/请假记录（后端 §2.3 lesson） */
export interface Lesson {
  id: number
  studentId: number
  courseId: number
  slotId: number
  lessonDate: string
  status: LessonStatus
  absentBy: AbsentBy | null
  absentReason: string | null
  makeUpDate: string | null
  /** 待补显示控制，与 status 正交 */
  closed: boolean
  remark: string
}

/**
 * 网格/今日/待补视图用的单元格（BE-API-15/23/33 返回）
 * 在 Lesson 基础上补充展示字段（学生名、时段等）
 */
export interface LessonCell {
  id: number
  studentId: number
  studentName: string
  courseId: number
  slotId: number
  slotLabel?: string
  lessonDate: string
  status: LessonStatus
  absentBy: AbsentBy | null
  absentReason: string | null
  makeUpDate: string | null
  closed: boolean
  remark: string
}

/** 字典项（后端 §2.3 dict，type=grade|absent_reason） */
export interface Dict {
  id: number
  dictType: string
  dictValue: string
  sortOrder: number
  enabled: 0 | 1
}

/** 统一分页响应体 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/** 当月网格数据（BE-API-15） */
export interface MonthGrid {
  days: string[]
  slots: TimeSlot[]
  cells: Record<string, LessonCell> // key = `${date}#${slotId}`
}

/** 月总览统计（BE-API-30） */
export interface MonthStatistic {
  scheduled: number
  normal: number
  absent: number
  absentByStudent: number
  absentByTeacher: number
  madeUp: number
  cancelled: number
  pending: number
}

/** 统一返回体（AGENTS 4.2 / 前端概设 §2） */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

// ==================== 备课模块（题库 + 组卷） ====================

/** 语文知识点（BE-K-01 列表项，带该知识点通用题量） */
export interface KnowledgePoint {
  id: number
  name: string
  /** 适用年级，空 = 通用 */
  grade: string | null
  /** 大类：字词 / 句子 / 阅读 / 古诗文 / 写作 / 基础 */
  category: string | null
  parentId: number | null
  sortOrder: number
  enabled: 0 | 1
  /** 该知识点现有通用题量（组卷时的可用量） */
  questionCount: number
}

/** 题型 */
export type QuestionType = '选择' | '填空' | '判断' | '阅读' | '古诗文' | '写作' | '其他'

/** 题目（独立题库，可被多份试卷引用） */
export interface Question {
  id: number
  grade: string | null
  kpId: number | null
  kpName: string | null
  /** 归属学生，null = 通用题库 */
  studentId: number | null
  studentName: string | null
  qtype: string | null
  stem: string
  /** JSON 字符串数组（选择题选项），前端需 JSON.parse */
  options: string | null
  answer: string | null
  /** 解析（讲题要点） */
  analysis: string | null
  /** 难度 1 易 / 2 中 / 3 难 */
  difficulty: number
  source: string | null
  createTime?: string
  updateTime?: string
}

/** 试卷类型：KP 知识点专项卷 / LESSON 课时题单 */
export type PaperType = 'KP' | 'LESSON'
/** 试卷状态：DRAFT 草稿 / READY 可用 */
export type PaperStatus = 'DRAFT' | 'READY'

/** 试卷 */
export interface Paper {
  id: number
  title: string
  grade: string | null
  paperType: PaperType
  /** 归属学生，null = 通用卷 */
  studentId: number | null
  studentName: string | null
  /** 派生自哪份卷 */
  parentPaperId: number | null
  parentTitle: string | null
  status: PaperStatus
  remark: string | null
  questionCount: number
  createTime?: string
  updateTime?: string
}

/** 试卷详情（含按题号排列的题目） */
export interface PaperDetail extends Paper {
  questions: Question[]
}

/** 待备课行（BE-L-01） */
export interface TodoPrep {
  lessonId: number
  lessonDate: string
  slotId: number | null
  slotStart: string | null
  slotEnd: string | null
  studentId: number | null
  studentName: string | null
  grade: string | null
  lessonStatus: LessonStatus
  kpIds: number[]
  kpNames: string[]
  paperIds: number[]
  paperTitles: string[]
  /** 是否已备课（至少配了 1 个知识点） */
  prepared: boolean
  remark: string | null
}

/** 单节课备课详情（BE-L-02） */
export interface LessonPrep {
  lessonId: number
  studentId: number | null
  studentName: string | null
  grade: string | null
  lessonDate: string | null
  kpIds: number[]
  paperIds: number[]
  remark: string | null
}

// ==================== 智能排课（Spring AI Alibaba） ====================

/** AI 排课动作（type：CREATE 新增 / DELETE 删除 / UPDATE 调整） */
export interface AiAction {
  type: 'CREATE' | 'DELETE' | 'UPDATE' | string
  /** 课次 id（DELETE / UPDATE 用） */
  lessonId: number | null
  studentId: number | null
  studentName: string | null
  /** yyyy-MM-dd */
  lessonDate: string | null
  slotId: number | null
  slotLabel: string | null
  /** 人类可读的动作说明 */
  note: string | null
  /** 非空表示不可执行（如该时段已被占用） */
  blocked: string | null

  // 顺延 / 补课
  absentBy?: string | null
  absentReason?: string | null
  makeUpDate?: string | null

  // 批量循环
  weekdays?: number[] | null
  startDate?: string | null
  endDate?: string | null

  // 复制课表
  sourceFrom?: string | null
  targetFrom?: string | null
}

/** 多轮对话历史项（旧 → 新） */
export interface AiHistoryItem {
  role: 'user' | 'ai'
  text: string
}

/** AI 反问的候选项（点选式引导） */
export interface AiAskOption {
  /** 机器值：学生 id / 日期 / 时段 id 等 */
  value: string
  /** 显示文本，如「张三 · 五年级」 */
  label: string
  /** 可选补充说明，如「本月已排 3 节」 */
  hint?: string | null
}

/** AI 反问：写操作缺参数时给出候选项，老师点选后继续 */
export interface AiAsk {
  /** 缺哪个参数：studentId / lessonDate / slotId / lessonId / absentBy / makeUpDate */
  field: string
  /** 问题文本，如「这一节排给哪位学生？」 */
  label: string
  options: AiAskOption[]
}

/** 老师对上一轮反问的点选结果（结构化回传，避免模型认错 id 与姓名） */
export interface AiSelection {
  field: string
  value: string
  label: string
}

/** AI 解析出的题目（/api/ai/parse-questions 返回，用于批量导入时补内容） */
export interface AiParsedQuestion {
  stem: string
  qtype: string
  options: string[]
  answer: string
  analysis: string
  difficulty: number
  /** 匹配到的知识点 id，只能取自传入的白名单 */
  kpId: number | null
  kpName: string | null
}

/** 智能排课对话结果（只出方案，不落库） */
export interface AiChatResult {
  reply: string
  actions: AiAction[]
  /** 缺参数时返回反问（与 actions 互斥；追问链走完才给 actions） */
  ask?: AiAsk | null
}

/** 确认执行结果 */
export interface AiApplyResult {
  created: number
  deleted: number
  updated: number
  failed: number
  messages: string[]
}

/** 排课记录（课节）列表行 —— 「排课记录」页用（BE-API-34） */
export interface LessonRecord {
  id: number
  lessonDate: string
  /** 星期：1=周一 … 7=周日 */
  weekday: number | null
  slotId: number | null
  slotStart: string | null
  slotEnd: string | null
  studentId: number | null
  studentName: string | null
  grade: string | null
  status: LessonStatus
  absentBy: AbsentBy | null
  absentReason: string | null
  makeUpDate: string | null
  /** 待补关闭 0/1 */
  closed: number | null
  remark: string | null
}
