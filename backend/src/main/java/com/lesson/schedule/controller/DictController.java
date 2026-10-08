package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.entity.Dict;
import com.lesson.schedule.service.DictService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典管理（S07）。路径对齐概设 §2.4.7 BE-API-26~29。
 * dict_type ∈ {grade, absent_reason}，供年级下拉与顺延原因弹出框复用。
 */
@RestController
@RequestMapping("/api/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /** BE-API-26 列表（按 type）。 */
    @GetMapping
    public Result<List<Dict>> list(@RequestParam String type) {
        return Result.success(dictService.listByType(type));
    }

    /** BE-API-27 新增。 */
    @PostMapping
    public Result<Long> create(@RequestBody Dict dict) {
        return Result.success(dictService.createDict(dict));
    }

    /** BE-API-28 修改。 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Dict dict) {
        dictService.updateDict(id, dict);
        return Result.success();
    }

    /** BE-API-29 删除。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictService.deleteDict(id);
        return Result.success();
    }
}
