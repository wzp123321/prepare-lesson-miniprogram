package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.AiApplyDTO;
import com.lesson.schedule.common.dto.AiChatDTO;
import com.lesson.schedule.common.vo.AiApplyResultVO;
import com.lesson.schedule.common.vo.AiChatVO;
import com.lesson.schedule.service.AiScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能排课（Spring AI × DeepSeek）。
 * <p>两阶段：chat 只出方案（不落库）→ 老师确认 → apply 执行。</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiScheduleController {

    private final AiScheduleService aiScheduleService;

    /** 对话：把自然语言指令翻译成排课方案。入参：message / year / month。 */
    @PostMapping("/schedule/chat")
    public Result<AiChatVO> chat(@RequestBody AiChatDTO dto) {
        return Result.success(aiScheduleService.chat(dto));
    }

    /** 确认执行方案。入参：actions。 */
    @PostMapping("/schedule/apply")
    public Result<AiApplyResultVO> apply(@RequestBody AiApplyDTO dto) {
        return Result.success(aiScheduleService.apply(dto));
    }
}
