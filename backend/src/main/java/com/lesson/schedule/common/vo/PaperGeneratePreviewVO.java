package com.lesson.schedule.common.vo;

import java.util.List;
import lombok.Data;

/**
 * 组卷试抽结果（不落库）。
 * 除题目本身外，还给回每个知识点的候选池余量，便于前端提示"某点题量不足"。
 */
@Data
public class PaperGeneratePreviewVO {

    /** 实际抽到的题目，顺序即预览题号顺序 */
    private List<QuestionVO> questions;

    /** 抽到的总题数 */
    private int total;

    /** 每个知识点的抽题情况 */
    private List<KpPickVO> picks;

    /** 知识点抽题明细 */
    @Data
    public static class KpPickVO {
        private Long kpId;
        private String kpName;
        /** 老师期望抽几题 */
        private int wanted;
        /** 题库里符合条件的总量 */
        private int available;
        /** 实际抽到几题（受题库余量与 maxTotal 影响） */
        private int picked;
    }
}
