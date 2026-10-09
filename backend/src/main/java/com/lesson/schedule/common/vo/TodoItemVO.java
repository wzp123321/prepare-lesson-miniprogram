package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import lombok.Data;

/**
 * 通用待办条目（BE-API-34 今日待办聚合出参的单条）。
 * <p>四类待办共用一种行结构，前端按 {@code category} 分组渲染即可，
 * 不必为每类各写一套表格。字段按「该行要做什么」而非「表结构」设计。</p>
 */
@Data
public class TodoItemVO {

    /** 分类：MARK_TODAY 今日课待标记 / MAKEUP_TODAY 今日该补 / PREP_TODAY 今日该上但未备课 / MAKEUP_OVERDUE 逾期待补 */
    private String category;

    /** 课次主键（点击处理时的落点） */
    private Long lessonId;

    /** 相关日期：待标记=今天；待补=原顺延日期；未备课=上课日期 */
    private LocalDate lessonDate;

    /** 学生 */
    private Long studentId;
    private String studentName;
    private String grade;

    /** 时段（形如 "09:00-10:30"，无时段时为 null） */
    private String slot;

    /** 课次状态 UNTAKEN / NORMAL / ABSENT / MADEUP / CANCELLED */
    private String status;

    /** 请假方 student / teacher（ABSENT 时有效） */
    private String absentBy;

    /** 顺延原因 */
    private String absentReason;

    /** 一句话说明「为什么出现在这里 / 要做什么」 */
    private String note;
}
