package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 试卷-题目编排。对应表 {@code paper_question}。
 * sortOrder 即卷面题号顺序。
 *
 * <p><b>卷内编辑</b>：{@code edited = 0} 表示这道题在本卷没被改过，查卷时读 {@code question} 表原题；
 * {@code edited = 1} 表示改过，卷面内容在 {@code paper_question_override} 表里（本表不存内容，
 * 所以编排表始终只有 5 列）。改卷不影响题库原题，也不影响其他引用同一题的试卷。
 */
@Data
@TableName("paper_question")
public class PaperQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    /** 来源题目（题库引用） */
    private Long questionId;

    /** 题号顺序 */
    private Integer sortOrder;

    /** 卷内是否编辑过 1/0；1 时内容见 paper_question_override */
    private Integer edited;
}
