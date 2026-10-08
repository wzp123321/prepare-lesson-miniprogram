package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 按学生查课请求体（BE-API-11）。
 */
@Data
public class CourseQueryDTO {

    /** 学生主键 */
    private Long studentId;
}
