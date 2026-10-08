package com.lesson.schedule.service.impl;

import com.lesson.schedule.common.vo.MonthIncomeVO;
import com.lesson.schedule.common.vo.MonthStatisticVO;
import com.lesson.schedule.common.vo.UnscheduledVO;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.entity.Lesson.AbsentBy;
import com.lesson.schedule.entity.Lesson.LessonStatus;
import com.lesson.schedule.mapper.StatisticsMapper;
import com.lesson.schedule.service.StatisticsService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据总览统计服务实现（S08，Wave G2）。
 * <p>采用「Mapper 取当月课次 + Java 内存聚合」策略（tasks-be 约束允许），
 * 避免复杂 SQL 聚合；收入计算按 Q2 口径严格使用 course.price（由 StatisticsMapper 联表求和，BigDecimal 精确）。</p>
 */
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsMapper statisticsMapper;

    @Override
    public MonthStatisticVO monthStatistic(int year, int month) {
        List<Lesson> lessons = statisticsMapper.selectByMonth(year, month);

        MonthStatisticVO vo = new MonthStatisticVO();
        int scheduled = 0, normal = 0, absent = 0, madeUp = 0, cancelled = 0;
        int pending = 0, absentByStudent = 0, absentByTeacher = 0;

        if (lessons != null) {
            for (Lesson l : lessons) {
                scheduled++;
                String st = l.getStatus();
                if (LessonStatus.NORMAL.name().equals(st)) {
                    normal++;
                } else if (LessonStatus.ABSENT.name().equals(st)) {
                    absent++;
                    // 待补判定：ABSENT 且未关闭
                    if (l.getClosed() != null && l.getClosed() == 0) {
                        pending++;
                    }
                    // 顺延分类（仅 ABSENT 时 absentBy 有效）
                    if (AbsentBy.student.name().equals(l.getAbsentBy())) {
                        absentByStudent++;
                    } else if (AbsentBy.teacher.name().equals(l.getAbsentBy())) {
                        absentByTeacher++;
                    }
                } else if (LessonStatus.MADEUP.name().equals(st)) {
                    madeUp++;
                } else if (LessonStatus.CANCELLED.name().equals(st)) {
                    cancelled++;
                }
                // UNTAKEN 计入 scheduled，但不计入上述任一分类
            }
        }

        vo.setScheduled(scheduled);
        vo.setNormal(normal);
        vo.setAbsent(absent);
        vo.setMadeUp(madeUp);
        vo.setCancelled(cancelled);
        vo.setPending(pending);
        vo.setAbsentByStudent(absentByStudent);
        vo.setAbsentByTeacher(absentByTeacher);
        return vo;
    }

    @Override
    public List<UnscheduledVO> monthDetail(int year, int month) {
        List<UnscheduledVO> list = statisticsMapper.selectMonthUnscheduled(year, month);
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    public MonthIncomeVO monthIncome(int year, int month) {
        BigDecimal income = statisticsMapper.selectMonthIncome(year, month);
        if (income == null) {
            income = BigDecimal.ZERO;
        }
        MonthIncomeVO vo = new MonthIncomeVO();
        vo.setIncome(income);
        vo.setFormula("Σ(NORMAL+MADEUP 课时 × course.price)");
        return vo;
    }
}
