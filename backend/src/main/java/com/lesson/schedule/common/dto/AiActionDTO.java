package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/**
 * AI 排课动作（既作对话出参、又作确认执行的入参）。
 * <p>type=CREATE 新增 / DELETE 删除 / UPDATE 调整（换时段或日期）。</p>
 */
@Data
public class AiActionDTO {

    /** CREATE / DELETE / UPDATE */
    private String type;

    /** 课次 id（DELETE / UPDATE 必填，来自「该月已有排课」上下文） */
    private Long lessonId;

    private Long studentId;

    /** 学生姓名（回显，便于老师核对） */
    private String studentName;

    /** 日期 yyyy-MM-dd（CREATE / UPDATE 必填） */
    private String lessonDate;

    /** 时段 id（CREATE / UPDATE 必填） */
    private Long slotId;

    /** 时段起止（回显） */
    private String slotLabel;

    /** 人类可读的动作说明 */
    private String note;

    /** 不可执行原因（如「该时段已排李四」），非空则前端置灰、后端跳过 */
    private String blocked;

    // ===== 顺延 / 补课（type=ABSENT / MAKEUP_ARRANGE / MAKEUP_DONE）=====

    /** 请假方 student / teacher（ABSENT 必填） */
    private String absentBy;

    /** 顺延原因（ABSENT 必填） */
    private String absentReason;

    /** 补课日期 yyyy-MM-dd（MAKEUP_ARRANGE 必填） */
    private String makeUpDate;

    // ===== 批量循环（type=BATCH_CREATE）=====

    /** 星期几 1..7（BATCH_CREATE 必填） */
    private List<Integer> weekdays;

    private String startDate;

    private String endDate;

    // ===== 复制课表（type=COPY_WEEK）=====

    /** 源周起始日 yyyy-MM-dd（COPY_WEEK 必填） */
    private String sourceFrom;

    /** 目标周起始日 yyyy-MM-dd（COPY_WEEK 必填） */
    private String targetFrom;
}
