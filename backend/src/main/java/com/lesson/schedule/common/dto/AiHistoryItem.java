package com.lesson.schedule.common.dto;

import lombok.Data;

/** 多轮对话中的一条历史消息（前端回传，用于承接「改成周日上午」这类追问）。 */
@Data
public class AiHistoryItem {

    /** user = 老师说的，ai = 助手说的 */
    private String role;

    private String text;
}
