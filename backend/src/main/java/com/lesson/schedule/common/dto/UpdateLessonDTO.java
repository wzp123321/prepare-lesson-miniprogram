package com.lesson.schedule.common.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 改课请求（BE-API-18）。换时段 / 换日期，courseId 不变（同一门课）。
 */
@Data
public class UpdateLessonDTO {

    /** 时间段（网格列） */
    @NotNull(message = "时间段不能为空")
    private Long slotId;

    /** 上课日期，必须属当前月，否则 409（只排当月约束，C-07） */
    @NotNull(message = "上课日期不能为空")
    private LocalDate lessonDate;
}
