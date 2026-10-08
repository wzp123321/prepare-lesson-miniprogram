package com.lesson.schedule.common.dto;

import lombok.Data;

/** 知识点列表查询。入参：grade / category / enabled（均可空）。 */
@Data
public class KpQueryDTO {

    private String grade;
    private String category;
    private Integer enabled;
}
