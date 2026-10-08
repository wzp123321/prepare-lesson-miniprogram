package com.lesson.schedule.common.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存当月排课单元（BE-API-19 请求体元素）。
 * <p>一个对象代表网格中「某日期 + 某时段 + 某学生」的一格。courseId 由后端按 studentId 唯一课程解析，不暴露给前端。</p>
 */
@Data
public class LessonSaveDTO {

    /** 学生 */
    @NotNull(message = "学生不能为空")
    private Long studentId;

    /** 时间段（网格列） */
    @NotNull(message = "时间段不能为空")
    private Long slotId;

    /** 上课日期，必须属目标当月，否则 400（只排当月约束） */
    @NotNull(message = "上课日期不能为空")
    private LocalDate lessonDate;
}
