package com.lesson.schedule.common.dto;

import java.time.LocalDate;
import lombok.Data;

/**
 * 排课记录查询（分页）。入参：studentId / grade / from / to / status / page / size。
 * <p>grade 来自 student 表（lesson 本身不存年级），服务层先按年级取出学生 id 再过滤。</p>
 */
@Data
public class LessonQueryDTO {

    /** 指定学生 */
    private Long studentId;

    /** 指定年级（匹配 student.grade） */
    private String grade;

    /** 起始日期（含） */
    private LocalDate from;

    /** 结束日期（含） */
    private LocalDate to;

    /** 状态：UNTAKEN / NORMAL / ABSENT / MADEUP / CANCELLED */
    private String status;

    private Long page = 1L;

    private Long size = 20L;
}
