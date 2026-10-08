package com.lesson.schedule.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存当月请求（BE-API-19，按用户决策改版）。
 * <p>前端只发 {@code {year, month}}，后端假定 BE-API-16/17/18 已逐条落库，
 * save-month 仅做「关闭上月顺延 + 返回 closedCount」，不再接收网格变更集。</p>
 */
@Data
public class SaveMonthDTO {

    /** 目标年（决定「上月」边界） */
    @NotNull(message = "year 不能为空")
    private Integer year;

    /** 目标月 */
    @NotNull(message = "month 不能为空")
    private Integer month;
}
