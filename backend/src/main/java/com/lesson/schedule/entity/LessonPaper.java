package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课次-试卷。对应表 {@code lesson_paper}。
 * 一节课可使用多份试卷（跨卷取题）。
 */
@Data
@TableName("lesson_paper")
public class LessonPaper {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lessonId;

    private Long paperId;
}
