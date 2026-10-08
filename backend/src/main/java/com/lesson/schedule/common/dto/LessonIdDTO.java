package com.lesson.schedule.common.dto;

import lombok.Data;

/** 按课次查询（备课模块）。入参：lessonId。 */
@Data
public class LessonIdDTO {

    private Long lessonId;
}
