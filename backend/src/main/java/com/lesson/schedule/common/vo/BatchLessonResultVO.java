package com.lesson.schedule.common.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** BE-API-16B 批量排课结果：新建数、跳过数、被跳过的日期（该格已有课或非当前月）。 */
@Data
public class BatchLessonResultVO {

    /** 成功新建的课次数 */
    private int created;

    /** 跳过的日期数（每格仅 1 人已占用 / 非当前月） */
    private int skipped;

    /** 被跳过的日期列表（yyyy-MM-dd），便于前端提示 */
    private List<String> skippedDates = new ArrayList<>();
}
