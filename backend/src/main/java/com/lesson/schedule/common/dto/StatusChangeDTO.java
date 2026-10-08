package com.lesson.schedule.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 状态切换请求体（BE-API-22）。status ∈ {NORMAL, MADEUP, CANCELLED}；
 * 标记为顺延(ABSENT)请走 {@code /lessons/absent} 专用接口。
 * id 在动作化改造后由 body 传入（原路径变量已移除）。
 */
@Data
public class StatusChangeDTO {

    /** 排课 ID */
    private Long id;

    /** 目标状态：NORMAL / MADEUP / CANCELLED */
    @NotBlank(message = "状态(status)不能为空")
    private String status;
}
