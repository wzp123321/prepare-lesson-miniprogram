package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 手动关闭待补请求体（BE-API-25）。字段名用 lessonId 以契合契约。
 */
@Data
public class MakeUpCloseDTO {

    /** 排课主键（契约中为 lessonId） */
    private Long lessonId;
}
