package com.lesson.schedule.common.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 拖拽建课请求（BE-API-16）。courseId 由后端按 studentId 唯一课程解析，不暴露给前端。
 */
@Data
public class CreateLessonDTO {

    /** 学生 */
    @NotNull(message = "学生不能为空")
    private Long studentId;

    /** 时间段（网格列） */
    @NotNull(message = "时间段不能为空")
    private Long slotId;

    /** 上课日期，必须属当前月，否则 409（只排当月约束，C-07） */
    @NotNull(message = "上课日期不能为空")
    private LocalDate lessonDate;
}
