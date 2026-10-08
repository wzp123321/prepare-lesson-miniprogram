package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.DictQueryDTO;
import com.lesson.schedule.common.dto.IdDTO;
import com.lesson.schedule.entity.Dict;
import com.lesson.schedule.service.DictService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典管理（S07）。路径对齐概设 §2.4.7 BE-API-26~29。
 * dict_type ∈ {grade, absent_reason}，供年级下拉与顺延原因弹出框复用。
 * 统一 POST + 动作化路径，参数全部走 {@code @RequestBody}。
 */
@RestController
@RequestMapping("/api/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /** BE-API-26 列表（按 type）。入参：type。 */
    @PostMapping("/list")
    public Result<List<Dict>> list(@RequestBody DictQueryDTO dto) {
        return Result.success(dictService.listByType(dto.getType()));
    }

    /** BE-API-27 新增。入参：dictType / dictValue / sortOrder / enabled。 */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody Dict dict) {
        return Result.success(dictService.createDict(dict));
    }

    /** BE-API-28 修改。入参：id + 字典各字段。 */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody Dict dict) {
        dictService.updateDict(dict.getId(), dict);
        return Result.success();
    }

    /** BE-API-29 删除。入参：id。 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody IdDTO dto) {
        dictService.deleteDict(dto.getId());
        return Result.success();
    }
}
