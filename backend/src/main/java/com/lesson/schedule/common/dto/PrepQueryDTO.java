package com.lesson.schedule.common.dto;

import java.time.LocalDate;
import lombok.Data;

/** 待备课列表查询区间。入参：from / to（均为日期）。 */
@Data
public class PrepQueryDTO {

    private LocalDate from;
    private LocalDate to;
}
