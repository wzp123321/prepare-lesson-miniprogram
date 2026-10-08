package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 课程创建/修改请求体（BE-API-12/13）。
 * <p>每生一门语文课，price 由后端冗余自 student.price；subject 固定「语文」。</p>
 * <ul>
 *   <li>BE-API-12 创建：studentId 必填，id 忽略</li>
 *   <li>BE-API-13 修改备注：id 必填，studentId 忽略</li>
 * </ul>
 */
@Data
public class CourseCreateDTO {

    /** 课程 ID（BE-API-13 修改备注时必填；BE-API-12 创建时忽略） */
    private Long id;

    /** 学生 ID（BE-API-12 创建时必填；BE-API-13 忽略） */
    private Long studentId;

    /** 备注（选填） */
    private String remark;
}
