package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 课程创建/修改请求体（BE-API-12/13）。
 * <p>每生一门语文课，price 由后端冗余自 student.price；subject 固定「语文」。</p>
 */
@Data
public class CourseCreateDTO {

    /** 备注（选填） */
    private String remark;
}
