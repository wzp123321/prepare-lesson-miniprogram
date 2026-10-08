package com.lesson.schedule.common.vo;

import com.lesson.schedule.entity.TimeSlot;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 当月课表网格（BE-API-15 出参）。前后台共用同一结构。
 * <ul>
 *   <li>days：当月所有日期（yyyy-MM-dd），网格行</li>
 *   <li>slots：时段列表（按开始时间升序），网格列</li>
 *   <li>cells：key = {@code lessonDate#slotId}，value = 该格排课（无则缺省）</li>
 * </ul>
 */
@Data
public class MonthGridVO {

    private int year;
    private int month;

    /** 当月日期序列（行） */
    private List<String> days;

    /** 时段序列（列） */
    private List<TimeSlot> slots;

    /** 日期#时段 → 单元映射 */
    private Map<String, LessonCellVO> cells;
}
