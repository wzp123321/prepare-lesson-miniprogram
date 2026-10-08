package com.lesson.schedule.common.dto;

import lombok.Data;

/** 试卷列表查询。入参：grade / studentId / paperType / keyword（均可空）。 */
@Data
public class PaperQueryDTO {

    private String grade;
    private Long studentId;
    private String paperType;
    private String keyword;
}
