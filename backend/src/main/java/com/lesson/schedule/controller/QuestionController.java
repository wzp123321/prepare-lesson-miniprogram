package com.lesson.schedule.controller;

import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.BatchQuestionDTO;
import com.lesson.schedule.common.dto.IdDTO;
import com.lesson.schedule.common.dto.QuestionDupCheckDTO;
import com.lesson.schedule.common.dto.QuestionQueryDTO;
import com.lesson.schedule.common.vo.QuestionDupCheckVO;
import com.lesson.schedule.common.vo.QuestionVO;
import com.lesson.schedule.entity.Question;
import com.lesson.schedule.service.QuestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题库（备课模块 BE-Q-01~05）。
 * 统一 POST + 动作化路径，参数全部走 {@code @RequestBody}。
 */
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /** BE-Q-01 分页列表+筛选。入参：grade / kpId / qtype / keyword / studentId / page / size。 */
    @PostMapping("/list")
    public Result<PageResult<QuestionVO>> list(@RequestBody QuestionQueryDTO dto) {
        return Result.success(questionService.list(dto));
    }

    /** BE-Q-02 详情。入参：id。 */
    @PostMapping("/get")
    public Result<QuestionVO> get(@RequestBody IdDTO dto) {
        return Result.success(questionService.get(dto.getId()));
    }

    /** BE-Q-03 录题。入参：grade / kpId / qtype / stem / options / answer / analysis / difficulty / source。 */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody Question question) {
        return Result.success(questionService.create(question));
    }

    /** 批量入库（粘贴整卷导入）。入参：questions 数组；返回成功入库条数。 */
    @PostMapping("/batch")
    public Result<Integer> batch(@RequestBody BatchQuestionDTO dto) {
        return Result.success(questionService.batchCreate(dto == null ? null : dto.getQuestions()));
    }

    /**
     * 导入前查重。入参：stems 题干数组；返回与入参同序的查重结果。
     * 供「批量导入」核对表格标注重复题，避免同题反复入库。
     */
    @PostMapping("/check-duplicates")
    public Result<List<QuestionDupCheckVO>> checkDuplicates(@RequestBody QuestionDupCheckDTO dto) {
        return Result.success(questionService.checkDuplicates(dto == null ? null : dto.getStems()));
    }

    /** BE-Q-04 改题。入参：id + 各字段。 */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody Question question) {
        questionService.update(question.getId(), question);
        return Result.success();
    }

    /** BE-Q-05 删题（被卷引用时 409）。入参：id。 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody IdDTO dto) {
        questionService.delete(dto.getId());
        return Result.success();
    }
}
