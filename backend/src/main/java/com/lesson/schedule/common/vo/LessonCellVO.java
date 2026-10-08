package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import lombok.Data;

/**
 * 当月网格单元视图（BE-API-15 出参 cells 的元素）。
 * <p>join {@code student} 取姓名与专属色，便于前后台直接渲染课表格，无需二次请求。</p>
 */
@Data
public class LessonCellVO {

    /** lesson 主键 */
    private Long id;

    /** 上课日期 */
    private LocalDate lessonDate;

    /** 时间段（网格列） */
    private Long slotId;

    /** 学生 */
    private Long studentId;

    /** 学生姓名（join student） */
    private String studentName;

    /** 学生专属色 hex（join student） */
    private String color;

    /** 状态：LessonStatus 枚举值 */
    private String status;

    /** 待补关闭 0/1 */
    private Integer closed;

    /** 请假方 student/teacher（ABSENT 时有效） */
    private String absentBy;

    /** 顺延原因（ABSENT 时） */
    private String absentReason;

    /** 补课日期（MADEUP 时承载，可跨月） */
    private LocalDate makeUpDate;
}
