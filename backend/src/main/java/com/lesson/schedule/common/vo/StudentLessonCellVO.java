package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/**
 * 学生当月课表单元（出图数据列表元素，BE-API-20）。
 * <p>join {@code time_slot} 取起止时间，便于前端 html2canvas 直接出 PNG 网格，无需二次请求。</p>
 */
@Data
public class StudentLessonCellVO {

    /** 上课日期 */
    private LocalDate date;

    /** 时间段（网格列） */
    private Long slotId;

    /** 时段起（join time_slot.start_time） */
    private LocalTime slotStart;

    /** 时段止（join time_slot.end_time） */
    private LocalTime slotEnd;

    /** 状态：LessonStatus 枚举值 */
    private String status;

    /** 请假方 student/teacher（ABSENT 时有效） */
    private String absentBy;

    /** 顺延原因（ABSENT 时，来自 lesson.absent_reason） */
    private String reason;

    /** 补课日期（MADEUP 时承载，可跨月） */
    private LocalDate makeUpDate;
}
