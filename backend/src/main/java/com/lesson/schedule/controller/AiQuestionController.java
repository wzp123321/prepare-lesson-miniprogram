package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.AiParseQuestionDTO;
import com.lesson.schedule.common.vo.AiParsedQuestionVO;
import com.lesson.schedule.service.AiQuestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 题库助手（Spring AI × DeepSeek）。
 * <p>把批量粘贴的题目原文整理成结构化字段，供老师核对后批量入库。</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiQuestionController {

    private final AiQuestionService aiQuestionService;

    /**
     * 批量解析题目：补答案 / 解析 / 难度 / 知识点。
     * 入参：items（逐题原文）+ grade + source + kpOptions（知识点白名单）。
     */
    @PostMapping("/parse-questions")
    public Result<List<AiParsedQuestionVO>> parseQuestions(@RequestBody AiParseQuestionDTO dto) {
        return Result.success(aiQuestionService.parse(dto));
    }
}
