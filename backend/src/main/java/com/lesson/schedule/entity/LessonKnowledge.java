package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课次-知识点。对应表 {@code lesson_knowledge}。
 * 「一课多点」：一节课可讲多个知识点。
 */
@Data
@TableName("lesson_knowledge")
public class LessonKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lessonId;

    private Long kpId;
}
