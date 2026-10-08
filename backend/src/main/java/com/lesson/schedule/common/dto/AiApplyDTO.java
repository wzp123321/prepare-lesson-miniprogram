package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/** 确认执行 AI 排课方案。入参：actions。 */
@Data
public class AiApplyDTO {

    private List<AiActionDTO> actions;
}
