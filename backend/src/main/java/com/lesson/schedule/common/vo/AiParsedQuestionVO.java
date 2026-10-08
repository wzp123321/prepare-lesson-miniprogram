package com.lesson.schedule.common.vo;

import java.util.List;
import lombok.Data;

/** AI 解析出的单道题。 */
@Data
public class AiParsedQuestionVO {

    /** 题干（保留原文） */
    private String stem;

    /** 题型：选择 / 填空 / 判断 / 阅读 / 古诗文 / 写作 / 其他 */
    private String qtype;

    /** 选择题选项，如 ["A. 张三", "B. 李四"]；非选择题为空数组 */
    private List<String> options;

    /** 答案：原文有就照抄，没有则由模型给参考答案 */
    private String answer;

    /** 解析（讲题要点） */
    private String analysis;

    /** 难度 1 易 / 2 中 / 3 难 */
    private Integer difficulty;

    /** 匹配到的知识点 id（只能取自传入的白名单，否则为 null） */
    private Long kpId;

    /** 知识点名（回显用） */
    private String kpName;
}
