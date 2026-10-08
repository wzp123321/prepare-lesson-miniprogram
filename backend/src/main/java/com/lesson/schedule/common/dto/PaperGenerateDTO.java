package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/**
 * 一键组卷入参：按知识点随机抽题成卷。
 * countPerKp 为每个知识点抽取的题量；某知识点题量不足时按其现有题量抽取。
 */
@Data
public class PaperGenerateDTO {

    private String title;
    private String grade;
    /** 勾选的知识点 */
    private List<Long> kpIds;
    /** 每个知识点抽几题 */
    private Integer countPerKp;
    /** 难度筛选：1 易 / 2 中 / 3 难；null 或 0 = 不限 */
    private Integer difficulty;
    /** 默认 KP */
    private String paperType;
    /** 归属学生，空 = 通用卷 */
    private Long studentId;
}
