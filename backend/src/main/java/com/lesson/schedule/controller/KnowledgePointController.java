package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.IdDTO;
import com.lesson.schedule.common.dto.KpQueryDTO;
import com.lesson.schedule.common.vo.KpVO;
import com.lesson.schedule.entity.KnowledgePoint;
import com.lesson.schedule.service.KnowledgePointService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识点管理（备课模块 BE-K-01~04）。
 * 统一 POST + 动作化路径，参数全部走 {@code @RequestBody}。
 */
@RestController
@RequestMapping("/api/kp")
@RequiredArgsConstructor
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    /** BE-K-01 列表（按年级 / 大类 / 启用过滤，带题量）。入参：grade / category / enabled。 */
    @PostMapping("/list")
    public Result<List<KpVO>> list(@RequestBody KpQueryDTO dto) {
        return Result.success(knowledgePointService.list(dto));
    }

    /** BE-K-02 新增。入参：name / grade / category / parentId / sortOrder / enabled。 */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody KnowledgePoint kp) {
        return Result.success(knowledgePointService.create(kp));
    }

    /** BE-K-03 修改。入参：id + 各字段。 */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody KnowledgePoint kp) {
        knowledgePointService.update(kp.getId(), kp);
        return Result.success();
    }

    /** BE-K-04 删除（被题目 / 课次引用时 409）。入参：id。 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody IdDTO dto) {
        knowledgePointService.delete(dto.getId());
        return Result.success();
    }
}
