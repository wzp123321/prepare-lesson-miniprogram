package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 字典列表查询请求体（BE-API-26）。
 */
@Data
public class DictQueryDTO {

    /** 字典类型：grade（年级）/ absent_reason（顺延原因） */
    private String type;
}
