package com.lesson.schedule.common.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 按月总览卡片视图（BE-API-30 / S08 数据总览）。
 * <p>口径对照（概设 §2.3 / BE-API-30）：
 * N=scheduled / X=normal / Y=absent / Z=madeUp / W=cancelled / K=pending(ABSENT&closed=0)；
 * Y 拆 absentByStudent / absentByTeacher 两类。月收入见独立端点 BE-API-32（MonthIncomeVO）。</p>
 */
@Data
public class MonthStatisticVO {

    /** 当月 lesson 总数（排课数 N，契约字段 scheduled） */
    private int scheduled;

    /** status=NORMAL 计数（X，正常上课） */
    private int normal;

    /** status=ABSENT 计数（Y，顺延合计，含已关闭与未关闭） */
    private int absent;

    /** status=MADEUP 计数（Z，已补，契约字段 madeUp） */
    private int madeUp;

    /** status=CANCELLED 计数（W，作废） */
    private int cancelled;

    /** status=ABSENT AND closed=0 计数（K，待补） */
    private int pending;

    /** ABSENT 中 absentBy='student' 计数 */
    private int absentByStudent;

    /** ABSENT 中 absentBy='teacher' 计数 */
    private int absentByTeacher;
}
