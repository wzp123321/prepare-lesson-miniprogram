package com.lesson.schedule.common.vo;

import lombok.Data;

/** 知识点（带题量，供组卷弹窗显示「该知识点现有 N 题」）。 */
@Data
public class KpVO {

    private Long id;
    private String name;
    private String grade;
    private String category;
    private Long parentId;
    private Integer sortOrder;
    private Integer enabled;

    /** 该知识点在通用题库中的题量 */
    private Long questionCount;
}
