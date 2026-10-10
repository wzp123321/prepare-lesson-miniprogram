package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 试卷-题目内容覆盖（卷内编辑）。对应表 {@code paper_question_override}。
 *
 * <p>只有「被编辑过」的编排项在这里才有行；未编辑的题不占行，查卷时回落 {@code question} 表。
 * 与 {@code paper_question} 是一对一（{@code paper_question_id} 唯一）。
 * 这样「改某份卷的题面」只影响本卷 —— 题库原题不动，其他引用同一题的卷也不动。
 */
@Data
@TableName("paper_question_override")
public class PaperQuestionOverride {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属编排项 paper_question.id */
    private Long paperQuestionId;

    /** 题型 */
    private String qtype;

    /** 题干 */
    private String stem;

    /** 选项 JSON 数组 */
    private String options;

    /** 答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 难度 1易/2中/3难 */
    private Integer difficulty;
}
