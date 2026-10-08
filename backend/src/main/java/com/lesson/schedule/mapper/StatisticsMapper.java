package com.lesson.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lesson.schedule.common.vo.UnscheduledVO;
import com.lesson.schedule.entity.Lesson;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 数据总览统计 Mapper（S08 / Wave G2）。承载 BE-API-30/31/32 全部统计查询，
 * 与排课核心 {@code LessonMapper} 解耦，避免改动 Wave G1 已交付的 lesson 文件。
 * <ul>
 *   <li>{@link #selectByMonth(int, int)} 指定年/月全部课次（总览卡片聚合用）</li>
 *   <li>{@link #selectMonthUnscheduled(int, int)} 历史月未上课明细（ABSENT + CANCELLED，契约字段）</li>
 *   <li>{@link #selectMonthIncome(int, int)} 月收入：Σ(NORMAL+MADEUP 课时 × course.price)（Q2 口径）</li>
 * </ul>
 */
public interface StatisticsMapper extends BaseMapper<Lesson> {

    /** 指定年/月全部课次（统计聚合用）。 */
    List<Lesson> selectByMonth(@Param("year") int year, @Param("month") int month);

    /** 指定年/月「未上课」明细（status=ABSENT 或 CANCELLED），按契约字段映射 lessonDate/studentName/slot/status/absentBy/absentReason。 */
    List<UnscheduledVO> selectMonthUnscheduled(@Param("year") int year, @Param("month") int month);

    /** 月收入合计：仅统计 NORMAL + MADEUP 课时，联 course 取 price（顺延未补/作废不计，Q2）。 */
    BigDecimal selectMonthIncome(@Param("year") int year, @Param("month") int month);
}
