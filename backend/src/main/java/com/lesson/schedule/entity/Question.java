package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 题目（独立题库）。对应表 {@code question}。
 * 同一道题可被多份试卷引用；派生卷会整行复制题目，改学生卷不影响通用卷。
 */
@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 年级 */
    private String grade;

    /** 考查知识点 */
    private Long kpId;

    /** 归属学生（NULL = 通用题库；派生卷克隆出的题带学生） */
    private Long studentId;

    /** 题型：选择 / 填空 / 判断 / 阅读 / 古诗文 / 写作 / 其他 */
    private String qtype;

    /** 题干 */
    private String stem;

    /** 选择题选项 JSON 数组，如 ["A. …","B. …"] */
    private String options;

    /** 答案 */
    private String answer;

    /** 解析（讲题要点） */
    private String analysis;

    /** 难度 1 易 / 2 中 / 3 难 */
    private Integer difficulty;

    /** 来源备注，如「2024 期末卷」 */
    private String source;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
