package com.lesson.schedule.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/**
 * BE-API-16B 批量/循环排课入参。
 *
 * <p>按「学生 + 时段 + 每周固定星期几 + 日期区间」批量生成排课，
 * 例如：每周三、周六 09:00 排满整个 10 月。
 */
@Data
public class BatchLessonDTO {

    @NotNull(message = "studentId 不能为空")
    private Long studentId;

    @NotNull(message = "slotId 不能为空")
    private Long slotId;

    /** 星期几（1=周一 … 7=周日），至少选一个 */
    @NotNull(message = "weekdays 不能为空")
    @Size(min = 1, message = "请至少选择一个星期")
    private List<Integer> weekdays;

    @NotNull(message = "startDate 不能为空")
    private LocalDate startDate;

    @NotNull(message = "endDate 不能为空")
    private LocalDate endDate;
}
