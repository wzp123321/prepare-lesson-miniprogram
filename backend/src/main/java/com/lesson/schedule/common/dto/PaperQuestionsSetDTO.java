package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/**
 * 试卷题目编排（整体覆盖）。
 * <p>{@code questionIds} 的顺序即题号顺序（兼容旧调用）。
 * <p>{@code items} 是新写法：每项可携带"卷内编辑内容"，同时给出顺序。
 * 两者同时存在时以 {@code items} 为准；只传 {@code questionIds} 时按未编辑处理。
 */
@Data
public class PaperQuestionsSetDTO {

    private Long paperId;

    /** 兼容写法：顺序即题号，全部视为未编辑 */
    private List<Long> questionIds;

    /** 新写法：可带卷内编辑内容的编排项（顺序即题号） */
    private List<Item> items;

    /** 单条编排项：来源题 + 可选卷内编辑内容 */
    @Data
    public static class Item {
        /** 来源题目 id（必填） */
        private Long questionId;
        /** 卷内是否编辑过（true 时，下方字段写入 paper_question_override） */
        private Boolean edited;
        private String qtype;
        private String stem;
        private String options;
        private String answer;
        private String analysis;
        private Integer difficulty;
    }
}
