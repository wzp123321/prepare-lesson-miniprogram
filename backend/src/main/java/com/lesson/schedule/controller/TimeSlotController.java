package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.TimeSlotSortDTO;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.service.TimeSlotService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 时间段管理（S02）。路径对齐概设 §2.4.2 BE-API-06~10。
 * 注意：TimeSlot 实体无 name 字段（概设 BE-API-07/08 示例中的 name 不在表结构中，本次不加字段，待确认）。
 */
@RestController
@RequestMapping("/api/time-slots")
@RequiredArgsConstructor
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    /** BE-API-06 列表（按 sortOrder）。 */
    @GetMapping
    public Result<List<TimeSlot>> list() {
        return Result.success(timeSlotService.listAll());
    }

    /** BE-API-07 新增（重叠校验，冲突 409）。 */
    @PostMapping
    public Result<Long> create(@RequestBody TimeSlot slot) {
        return Result.success(timeSlotService.createTimeSlot(slot));
    }

    /** BE-API-08 修改（重叠校验）。 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TimeSlot slot) {
        timeSlotService.updateTimeSlot(id, slot);
        return Result.success();
    }

    /** BE-API-09 删除（被引用 409 带计数）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        timeSlotService.deleteTimeSlot(id);
        return Result.success();
    }

    /** BE-API-10 排序调整（批量 [{id, sortOrder}]）。 */
    @PutMapping("/sort")
    public Result<Void> sort(@RequestBody List<TimeSlotSortDTO> items) {
        timeSlotService.updateSort(items);
        return Result.success();
    }
}
