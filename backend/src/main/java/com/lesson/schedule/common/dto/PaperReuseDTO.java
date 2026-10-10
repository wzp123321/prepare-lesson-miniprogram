package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 一键复用已有试卷：以某份卷为蓝本，生成一份**新的可编辑卷**。
 * 新卷沿用原卷的题目引用与编排顺序，以及各题的卷内编辑快照；原卷不动。
 * 入参：sourceId 蓝本卷 + 可选 title（默认「原标题 副本」）+ 可选 studentId（不放则沿用原卷归属）。
 */
@Data
public class PaperReuseDTO {

    /** 蓝本试卷 id（必填） */
    private Long sourceId;

    /** 新卷名；空则自动「原标题（副本）」 */
    private String title;

    /** 新卷归属学生；不传 = 沿用蓝本卷归属 */
    private Long studentId;
}
