package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 时间段排序调整项（BE-API-10 {@code PUT /api/time-slots/sort}）。
 */
@Data
public class TimeSlotSortDTO {

    /** 时间段主键 */
    private Long id;

    /** 目标排序值 */
    private Integer sortOrder;
}
