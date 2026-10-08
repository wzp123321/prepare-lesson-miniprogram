package com.lesson.schedule.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 标记顺延请求体（BE-API-21）。请假方与顺延原因均必填，缺失由 @Valid 拦截为 400。
 */
@Data
public class AbsentDTO {

    /** 请假方：student（学生请假）/ teacher（老师请假） */
    @NotBlank(message = "请假方(absentBy)不能为空")
    private String absentBy;

    /** 顺延原因（ABSENT 时必填） */
    @NotBlank(message = "顺延原因(absentReason)不能为空")
    private String absentReason;
}
