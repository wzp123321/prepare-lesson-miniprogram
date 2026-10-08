package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.vo.MonthIncomeVO;
import com.lesson.schedule.common.vo.MonthStatisticVO;
import com.lesson.schedule.common.vo.UnscheduledVO;
import com.lesson.schedule.service.StatisticsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据总览统计（S08，Wave G2）。路径/参数对齐 BE-API-30/31/32 契约：
 * <ul>
 *   <li>GET /api/statistics/month → 总览卡片（BE-API-30）</li>
 *   <li>GET /api/statistics/month/detail → 历史月未上课明细（BE-API-31）</li>
 *   <li>GET /api/statistics/month/income → 月收入（BE-API-32）</li>
 * </ul>
 * 月份入参统一为 {@code year}(int) + {@code month}(int)，与前端及 BE-API-15 一致（移除 YYYY-MM 字符串解析）。
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 按月总览卡片（BE-API-30）。N/X/Y/Z/W/K + 顺延分类。
     */
    @GetMapping("/month")
    public Result<MonthStatisticVO> month(@RequestParam int year,
                                          @RequestParam int month) {
        return Result.success(statisticsService.monthStatistic(year, month));
    }

    /**
     * 历史月未上课明细（BE-API-31，由旧 /history 改名对齐契约）。ABSENT 未补 + CANCELLED。
     */
    @GetMapping("/month/detail")
    public Result<List<UnscheduledVO>> monthDetail(@RequestParam int year,
                                                   @RequestParam int month) {
        return Result.success(statisticsService.monthDetail(year, month));
    }

    /**
     * 月收入（BE-API-32，Q2 口径）。income = Σ(NORMAL+MADEUP 课时 × course.price)；顺延未补/作废不计。
     */
    @GetMapping("/month/income")
    public Result<MonthIncomeVO> monthIncome(@RequestParam int year,
                                             @RequestParam int month) {
        return Result.success(statisticsService.monthIncome(year, month));
    }
}
