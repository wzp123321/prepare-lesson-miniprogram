package com.lesson.schedule.service;

import com.lesson.schedule.common.dto.AiParseQuestionDTO;
import com.lesson.schedule.common.vo.AiParsedQuestionVO;
import java.util.List;

/**
 * AI 题库助手：把「粘贴进来的一堆题目原文」整理成结构化字段（答案 / 解析 / 难度 / 知识点）。
 * <p>规则切题由前端完成，这里只负责补内容。</p>
 */
public interface AiQuestionService {

    /** 批量解析。未配置 API Key 时抛 500 并给出明确提示，前端据此降级为纯规则导入。 */
    List<AiParsedQuestionVO> parse(AiParseQuestionDTO dto);
}
