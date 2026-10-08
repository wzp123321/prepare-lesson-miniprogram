package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/**
 * 今日课程单元视图（BE-API-33 出参 lessons 元素）。
 * <p>join {@code student} 取姓名、{@code course} 取科目、{@code time_slot} 取时段起止，
 * 便于今日落地页直接渲染，无需二次请求。</p>
 */
@Data
public class TodayLessonVO {

    /** lesson 主键 */
    private Long id;

    /** 上课日期 */
    private LocalDate lessonDate;

    /** 时间段（网格列） */
    private Long slotId;

    /** 时段起 */
    private LocalTime slotStart;

    /** 时段止 */
    private LocalTime slotEnd;

    /** 学生 */
    private Long studentId;

    /** 学生姓名（join student） */
    private String studentName;

    /** 课程科目（join course，固定「语文」） */
    private String subject;

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
