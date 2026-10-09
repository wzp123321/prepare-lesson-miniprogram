// 今日待办（BE-API-34）—— 系统落地页数据源，只读聚合
import request from '@/utils/request'
import type { TodayTodo, TodoItem } from '@/types'

// BE-API-34 今日待办聚合（无入参）
export function fetchTodayTodo(): Promise<TodayTodo> {
  return request.post<TodayTodo, TodayTodo>('/dashboard/today-todo')
}

/** 待办分类 → 展示元信息（图标名 / 标题 / 处理入口路由） */
export interface TodoCategoryMeta {
  title: string
  hint: string
  /** 点击「去处理」跳转的路由 */
  route: string
}

export const TODO_CATEGORY_META: Record<TodoItem['category'], TodoCategoryMeta> = {
  MARK_TODAY: {
    title: '今天有课 · 待标记',
    hint: '上完课后标记「正常上课」或「顺延」，标记后才会进入收入与统计',
    route: '/dashboard/today'
  },
  MAKEUP_TODAY: {
    title: '今天该补的课',
    hint: '补完课后点「已补」，会计入当月已补节数',
    route: '/dashboard/make-up'
  },
  MAKEUP_OVERDUE: {
    title: '逾期待补 · 该约时间了',
    hint: '顺延已久仍未安排补课，尽早跟学生确认时间',
    route: '/dashboard/make-up'
  },
  PREP_TODAY: {
    title: '明天要上 · 还没备课',
    hint: '提前一晚配好知识点与试卷，上课更省心',
    route: '/prep/todo'
  }
}
