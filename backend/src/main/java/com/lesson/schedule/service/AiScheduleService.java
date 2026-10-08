package com.lesson.schedule.service;

import com.lesson.schedule.common.dto.AiApplyDTO;
import com.lesson.schedule.common.dto.AiChatDTO;
import com.lesson.schedule.common.vo.AiApplyResultVO;
import com.lesson.schedule.common.vo.AiChatVO;

/**
 * 智能排课服务（Spring AI / DeepSeek）。
 * <p>两阶段：① chat 只出方案不落库；② apply 由老师确认后执行。</p>
 */
public interface AiScheduleService {

    /** 把自然语言指令翻译成排课方案（只读，不写库）。 */
    AiChatVO chat(AiChatDTO dto);

    /** 执行老师确认后的排课方案。 */
    AiApplyResultVO apply(AiApplyDTO dto);
}
