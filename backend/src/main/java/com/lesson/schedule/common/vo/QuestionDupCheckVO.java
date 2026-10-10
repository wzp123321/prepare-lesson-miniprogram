package com.lesson.schedule.common.vo;

import lombok.Data;

/**
 * 单条题干的查重结果。
 * <p>顺序与 {@code QuestionDupCheckDTO.stems} 一一对应，前端按下标回填。</p>
 */
@Data
public class QuestionDupCheckVO {

    /** 题库中疑似重复的题目 id；没命中为 null */
    private Long dupId;

    /** 疑似重复题的题干原文（截断用于提示）；没命中为 null */
    private String dupStem;

    /** true = 题干完全相同（归一化后），false = 仅高度相似 */
    private Boolean exact;
}
