package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.IdDTO;
import com.lesson.schedule.common.dto.TimeSlotSortDTO;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.service.TimeSlotService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 时间段管理（S02）。路径对齐概设 §2.4.2 BE-API-06~10。
 * 统一 POST + 动作化路径，参数全部走 {@code @RequestBody}。
 * 注意：TimeSlot 实体无 name 字段（概设 BE-API-07/08 示例中的 name 不在表结构中，本次不加字段，待确认）。
 */
@RestController
@RequestMapping("/api/time-slots")
@RequiredArgsConstructor
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    /** BE-API-06 列表（按 sortOrder）。无入参。 */
    @PostMapping("/list")
    public Result<List<TimeSlot>> list() {
        return Result.success(timeSlotService.listAll());
    }

    /** BE-API-07 新增（重叠校验，冲突 409）。入参：startTime / endTime / sortOrder / enabled。 */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody TimeSlot slot) {
        return Result.success(timeSlotService.createTimeSlot(slot));
    }

    /** BE-API-08 修改（重叠校验）。入参：id + 时间段各字段。 */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody TimeSlot slot) {
        timeSlotService.updateTimeSlot(slot.getId(), slot);
        return Result.success();
    }

    /** BE-API-09 删除（被引用 409 带计数）。入参：id。 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody IdDTO dto) {
        timeSlotService.deleteTimeSlot(dto.getId());
        return Result.success();
    }

    /** BE-API-10 排序调整（批量 [{id, sortOrder}]）。入参：有序主键列表。 */
    @PostMapping("/sort")
    public Result<Void> sort(@RequestBody List<TimeSlotSortDTO> items) {
        timeSlotService.updateSort(items);
        return Result.success();
    }
}
