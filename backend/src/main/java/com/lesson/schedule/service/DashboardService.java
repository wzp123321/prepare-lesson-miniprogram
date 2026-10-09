package com.lesson.schedule.service;

import com.lesson.schedule.common.vo.TodayTodoVO;

/**
 * 今日待办聚合服务（BE-API-34）。
 * <p>把「今天该做什么」从散落的 4 个页面合成一处：待标记 / 今日该补 / 明晚该备 / 逾期待补。
 * 本服务**只读**，不写任何状态；处理动作仍走 LessonService / PrepService 既有接口。</p>
 */
public interface DashboardService {

    /**
     * 聚合今日待办。
     *
     * @return 四类待办 + 合计数（各列表非 null，无数据为空列表）
     */
    TodayTodoVO todayTodo();
}
