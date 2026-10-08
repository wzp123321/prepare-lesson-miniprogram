package com.lesson.schedule.common.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 月收入视图（BE-API-32 / S08）。
 * <p>口径（Q2，概设 §2.3）：income = Σ(当月 NORMAL + MADEUP 课时 × course.price)；
 * 顺延未补(ABSENT)/作废(CANCELLED)不计入。formula 透出口径说明，便于前端展示。</p>
 */
@Data
public class MonthIncomeVO {

    /** 月收入合计（BigDecimal 精确，两位精度由 price 决定） */
    private BigDecimal income;

    /** 口径公式说明，如 "Σ(NORMAL+MADEUP 课时 × course.price)" */
    private String formula;
}
