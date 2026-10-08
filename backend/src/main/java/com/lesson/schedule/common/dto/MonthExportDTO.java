package com.lesson.schedule.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 排课表导出 Excel 请求。入参：year / month。 */
@Data
public class MonthExportDTO {

    @NotNull(message = "year 不能为空")
    private Integer year;

    @NotNull(message = "month 不能为空")
    private Integer month;
}
