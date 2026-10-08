package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import lombok.Data;

/**
 * 历史月「未上课」明细视图（BE-API-31 / S08）。
 * <p>列示指定月内 status=ABSENT（未补顺延）或 status=CANCELLED（作废）的课次，
 * 供历史月展示。ABSENT 未补与 CANCELLED 均属于「未实际上课」，故合列。
 * 字段对齐契约：lessonDate / studentName / slot / status / absentBy / absentReason。</p>
 */
@Data
public class UnscheduledVO {

    /** 上课日期（lesson_date，契约字段 lessonDate） */
    private LocalDate lessonDate;

    /** 学生姓名（join student） */
    private String studentName;

    /** 时间段标签（join time_slot，形如 "09:00-10:00"） */
    private String slot;

    /** 课时状态 ABSENT / CANCELLED（契约新增字段 status） */
    private String status;

    /** 请假方 student / teacher（仅 ABSENT 有效；CANCELLED 时为 null） */
    private String absentBy;

    /** 顺延原因 / 作废备注（lesson.absent_reason，契约字段 absentReason） */
    private String absentReason;
}
