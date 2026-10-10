package com.lesson.schedule.common.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 题目（带知识点名 / 学生名，列表与卷详情共用）。 */
@Data
public class QuestionVO {

    private Long id;
    private String grade;
    private Long kpId;
    private String kpName;
    private Long studentId;
    private String studentName;
    private String qtype;
    private String stem;
    /** JSON 字符串数组，前端 JSON.parse 后渲染 */
    private String options;
    private String answer;
    private String analysis;
    private Integer difficulty;
    private String source;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 是否「卷内已编辑」——仅试卷详情返回时可能为 true。
     * true 表示当前展示的题干/选项/答案等来自试卷快照，而非题库原题。
     * 题库列表接口恒为 null。
     */
    private Boolean edited;
}
