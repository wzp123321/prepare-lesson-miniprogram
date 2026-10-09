package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/**
 * 一键组卷入参：按知识点随机抽题成卷。
 *
 * <p>题量有两种给法，优先用 {@link #kpCounts}（可按知识点分别设量）：
 * <ul>
 *   <li>{@link #kpCounts}：每个知识点各自抽几题</li>
 *   <li>{@link #countPerKp}：所有知识点统一抽几题（兼容旧调用）</li>
 * </ul>
 * 某知识点题量不足时按其现有题量抽取。{@link #maxTotal} 为整卷题量上限。
 */
@Data
public class PaperGenerateDTO {

    private String title;
    private String grade;
    /** 勾选的知识点 */
    private List<Long> kpIds;
    /** 每个知识点抽几题（旧字段，kpCounts 为空时生效） */
    private Integer countPerKp;
    /** 按知识点分别设量；非空时优先于 countPerKp */
    private List<KpCountDTO> kpCounts;
    /** 整卷题量上限；null 或 <=0 = 不限 */
    private Integer maxTotal;
    /** 难度筛选：1 易 / 2 中 / 3 难；null 或 0 = 不限 */
    private Integer difficulty;
    /** 默认 KP */
    private String paperType;
    /** 归属学生，空 = 通用卷 */
    private Long studentId;
}
