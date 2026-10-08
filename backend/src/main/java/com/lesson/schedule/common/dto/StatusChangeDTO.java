package com.lesson.schedule.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 状态切换请求体（BE-API-22）。status ∈ {NORMAL, MADEUP, CANCELLED}；
 * 标记为顺延(ABSENT)请走 {@code /absent} 专用接口。
 */
@Data
public class StatusChangeDTO {

    /** 目标状态：NORMAL / MADEUP / CANCELLED */
    @NotBlank(message = "状态(status)不能为空")
    private String status;
}
