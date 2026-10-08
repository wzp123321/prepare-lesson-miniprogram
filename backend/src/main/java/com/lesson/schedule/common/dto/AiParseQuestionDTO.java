package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/** AI 解析题目的入参：把切分好的题目原文交给模型补答案 / 解析 / 难度 / 知识点。 */
@Data
public class AiParseQuestionDTO {

    /** 逐题的原文（可含选项、答案、解析）。一条 = 一道题。 */
    private List<String> items;

    /** 年级，仅作为提示 */
    private String grade;

    /** 可选知识点白名单：模型只能从中挑 kpId，不能编造 */
    private List<KpRef> kpOptions;

    /** 题目来源，仅作为提示（如「2024 期末卷」） */
    private String source;

    /** 知识点引用 */
    @Data
    public static class KpRef {
        private Long id;
        private String name;
    }
}
