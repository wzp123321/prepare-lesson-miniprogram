package com.lesson.schedule.common.vo;

import com.lesson.schedule.common.dto.AiActionDTO;
import java.util.List;
import lombok.Data;

/** 智能排课对话出参：AI 的说明 + 待确认的动作方案（不落库）。 */
@Data
public class AiChatVO {

    /** AI 的自然语言回复 */
    private String reply;

    /** 方案动作（前端逐条展示，老师确认后才执行） */
    private List<AiActionDTO> actions;

    /**
     * 缺参数时的反问（返回候选项让老师点选）。
     * <p>与 actions 互斥：出了 ask 就不要出 actions，参数齐了才出 actions。</p>
     */
    private AiAskVO ask;
}
