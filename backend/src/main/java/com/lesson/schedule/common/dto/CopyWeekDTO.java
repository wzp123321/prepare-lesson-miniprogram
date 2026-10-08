package com.lesson.schedule.common.dto;

import java.time.LocalDate;
import lombok.Data;

/**
 * 复制课表：把 sourceFrom 所在周（周一~周日）的课按同星期几平移到 targetFrom 所在周。
 * <p>两个日期均为「该周周一」，由前端换算后传入。</p>
 */
@Data
public class CopyWeekDTO {

    /** 源周周一 */
    private LocalDate sourceFrom;

    /** 目标周周一 */
    private LocalDate targetFrom;
}
