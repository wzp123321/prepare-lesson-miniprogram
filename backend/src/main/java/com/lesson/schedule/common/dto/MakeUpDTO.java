package com.lesson.schedule.common.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 安排补课请求体（BE-API-24 变体：仅写补课日期，status 保持 ABSENT 表示「已约」）。
 * 补课日期可跨月，不占新格。
 */
@Data
public class MakeUpDTO {

    /** 补课日期 DATE，可跨月 */
    @NotNull(message = "补课日期(makeUpDate)不能为空")
    private LocalDate makeUpDate;
}
