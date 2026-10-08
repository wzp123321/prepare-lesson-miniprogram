package com.lesson.schedule.service;

import com.lesson.schedule.common.vo.MonthIncomeVO;
import com.lesson.schedule.common.vo.MonthStatisticVO;
import com.lesson.schedule.common.vo.UnscheduledVO;
import java.util.List;

/**
 * 数据总览统计服务（S08，Wave G2）。承载按月聚合域规则（依 AGENTS 4.2 DDD，规则不散落 controller）。
 * <ul>
 *   <li>{@link #monthStatistic(int, int)} 总览卡片（BE-API-30）</li>
 *   <li>{@link #monthDetail(int, int)} 历史月未上课明细（BE-API-31）</li>
 *   <li>{@link #monthIncome(int, int)} 月收入（BE-API-32，Q2 口径）</li>
 * </ul>
 * 月收入口径严格按 Q2：(NORMAL + MADEUP) × course.price；ABSENT 未补 / CANCELLED 不计。
 */
public interface StatisticsService {

    /**
     * 按月总览统计（BE-API-30）。
     * <p>计数口径（概设 §2.3 / BE-API-30）：
     * scheduled=当月 lesson 总数；normal=NORMAL；absent=ABSENT；madeUp=MADEUP；
     * cancelled=CANCELLED；pending=ABSENT&closed=0；absentByStudent/absentByTeacher 为 ABSENT 内分类。</p>
     *
     * @param year  目标年
     * @param month 目标月
     * @return 总览卡片 VO
     */
    MonthStatisticVO monthStatistic(int year, int month);

    /**
     * 历史月未上课明细（BE-API-31）。
     * <p>返回指定月内 status=ABSENT（未补顺延）或 status=CANCELLED（作废）的课次，
     * 含 lessonDate/studentName/slot/status/absentBy/absentReason，按日期+时段升序。无则空列表。</p>
     *
     * @param year  目标年
     * @param month 目标月
     * @return 未上课明细列表（非 null）
     */
    List<UnscheduledVO> monthDetail(int year, int month);

    /**
     * 月收入（BE-API-32，Q2 口径）。
     * <p>income = Σ(当月 NORMAL + MADEUP 课时 × course.price)；顺延未补/作废不计。
     * 同时透出口径说明 formula。</p>
     *
     * @param year  目标年
     * @param month 目标月
     * @return 月收入 VO（income + formula）
     */
    MonthIncomeVO monthIncome(int year, int month);
}
