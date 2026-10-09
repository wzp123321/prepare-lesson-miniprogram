package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.vo.TodayTodoVO;
import com.lesson.schedule.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 今日待办（BE-API-34）—— 系统落地页的数据源。
 * <p>把原先散在「今日视图 / 待补课 / 待备课」三处的待办聚合成一处：
 * 今天有课未标记、今天该补、明晚该备、逾期待补。只读接口，无入参。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** BE-API-34 今日待办聚合。无入参，返回四类待办 + 合计。 */
    @PostMapping("/today-todo")
    public Result<TodayTodoVO> todayTodo() {
        return Result.success(dashboardService.todayTodo());
    }
}
