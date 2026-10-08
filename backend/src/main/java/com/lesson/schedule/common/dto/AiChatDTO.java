package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/** 智能排课对话入参。入参：message + 目标年月。 */
@Data
public class AiChatDTO {

    /** 老师的自然语言指令 */
    private String message;

    /** 目标年份（缺省按当前月） */
    private Integer year;

    /** 目标月份 */
    private Integer month;

    /** 多轮上下文：前端回传的最近若干轮对话（旧 → 新） */
    private List<AiHistoryItem> history;

    /** 老师对上一轮 ask 的点选结果（点选式引导：选学生 → 选日期 → 选时段） */
    private AiSelectionDTO selection;
}
