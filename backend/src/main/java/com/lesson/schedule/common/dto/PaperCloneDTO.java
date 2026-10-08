package com.lesson.schedule.common.dto;

import lombok.Data;

/** 试卷派生（clone）。入参：id 原卷 + studentId 目标学生。 */
@Data
public class PaperCloneDTO {

    private Long id;
    private Long studentId;
}
