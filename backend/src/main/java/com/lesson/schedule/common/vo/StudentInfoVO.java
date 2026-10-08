package com.lesson.schedule.common.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 出图数据中的学生基础信息（BE-API-20 学生当月课表出图）。
 * <p>仅含前端着色与展示所需字段：name / color / grade / price（依任务约束，不含联系方式等隐私字段）。</p>
 */
@Data
public class StudentInfoVO {

    /** 姓名 */
    private String name;

    /** 专属颜色 hex（课表着色） */
    private String color;

    /** 年级（取自 dict.grade） */
    private String grade;

    /** 课程价格 DECIMAL(10,2) 元/节 */
    private BigDecimal price;
}
