package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.IdDTO;
import com.lesson.schedule.common.dto.PaperCloneDTO;
import com.lesson.schedule.common.dto.PaperGenerateCommitDTO;
import com.lesson.schedule.common.dto.PaperGenerateDTO;
import com.lesson.schedule.common.dto.PaperQueryDTO;
import com.lesson.schedule.common.dto.PaperQuestionsSetDTO;
import com.lesson.schedule.common.dto.PaperReuseDTO;
import com.lesson.schedule.common.vo.PaperDetailVO;
import com.lesson.schedule.common.vo.PaperGeneratePreviewVO;
import com.lesson.schedule.common.vo.PaperVO;
import com.lesson.schedule.entity.Paper;
import com.lesson.schedule.service.PaperService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 试卷（备课模块 BE-P-01~08）。
 * 统一 POST + 动作化路径，参数全部走 {@code @RequestBody}。
 */
@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;

    /** BE-P-01 列表。入参：grade / studentId / paperType / keyword。 */
    @PostMapping("/list")
    public Result<List<PaperVO>> list(@RequestBody PaperQueryDTO dto) {
        return Result.success(paperService.list(dto));
    }

    /** BE-P-02 详情（含题目，按题号）。入参：id。 */
    @PostMapping("/get")
    public Result<PaperDetailVO> get(@RequestBody IdDTO dto) {
        return Result.success(paperService.get(dto.getId()));
    }

    /** BE-P-03 建卷。入参：title / grade / paperType / studentId / remark。 */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody Paper paper) {
        return Result.success(paperService.create(paper));
    }

    /** BE-P-04 改卷。入参：id + 各字段。 */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody Paper paper) {
        paperService.update(paper.getId(), paper);
        return Result.success();
    }

    /** BE-P-05 删卷（编排与课次关联一并清理，题目留在题库）。入参：id。 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody IdDTO dto) {
        paperService.delete(dto.getId());
        return Result.success();
    }

    /** BE-P-06 派生：克隆卷 + 克隆题目。入参：id / studentId。 */
    @PostMapping("/clone")
    public Result<Long> clone(@RequestBody PaperCloneDTO dto) {
        return Result.success(paperService.clone(dto.getId(), dto.getStudentId()));
    }

    /**
     * BE-P-11 一键复用：以蓝本卷生成一份新的可编辑卷。
     * 入参：sourceId（蓝本卷）/ title（可选新卷名）/ studentId（可选新归属，不传沿用蓝本）。
     * 复制编排与卷内编辑快照，不克隆题库行；原卷不动。
     */
    @PostMapping("/reuse")
    public Result<Long> reuse(@RequestBody PaperReuseDTO dto) {
        return Result.success(paperService.reuse(dto));
    }

    /** BE-P-07 编排题目（整体覆盖）。入参：paperId / questionIds（顺序即题号）。 */
    @PostMapping("/questions/set")
    public Result<Void> setQuestions(@RequestBody PaperQuestionsSetDTO dto) {
        paperService.setQuestions(dto);
        return Result.success();
    }

    /** BE-P-08 一键组卷（直接落库）。入参：title / grade / kpIds / countPerKp / difficulty。 */
    @PostMapping("/generate")
    public Result<Long> generate(@RequestBody PaperGenerateDTO dto) {
        return Result.success(paperService.generate(dto));
    }

    /**
     * BE-P-09 组卷试抽（不落库）。
     * 入参：title / grade / kpIds / kpCounts（按点设量）/ countPerKp / maxTotal / difficulty。
     * 返回题目列表 + 每个知识点的抽题明细，供老师预览、换题后再落库。
     */
    @PostMapping("/generate/preview")
    public Result<PaperGeneratePreviewVO> generatePreview(@RequestBody PaperGenerateDTO dto) {
        return Result.success(paperService.generatePreview(dto));
    }

    /** BE-P-10 组卷落库。入参：title / grade / paperType / studentId / questionIds（顺序即题号）。 */
    @PostMapping("/generate/commit")
    public Result<Long> generateCommit(@RequestBody PaperGenerateCommitDTO dto) {
        return Result.success(paperService.generateCommit(dto));
    }
}
