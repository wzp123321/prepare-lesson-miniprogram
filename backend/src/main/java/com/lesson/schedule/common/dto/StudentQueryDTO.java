package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 学生列表查询请求体（BE-API-01）。分页 + 关键字/年级/状态筛选。
 */
@Data
public class StudentQueryDTO {

    /** 关键字（姓名/电话模糊匹配，选填） */
    private String keyword;

    /** 年级（选填） */
    private String grade;

    /** 状态：1 在读 / 0 暂停归档（选填） */
    private Integer status;

    /** 页码，默认 1 */
    private long page = 1;

    /** 每页条数，默认 20 */
    private long size = 20;
}
