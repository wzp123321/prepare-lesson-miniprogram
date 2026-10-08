package com.lesson.schedule.service;

import com.lesson.schedule.entity.TimeSlot;
import java.util.List;

/**
 * 时间段服务（S02）。含列表（按开始时间升序）、新增/修改（重叠校验）、删除保护。
 */
public interface TimeSlotService {

    /** BE-API-06 列表（按 start_time 升序，不提供手工排序）。 */
    List<TimeSlot> listAll();

    /** BE-API-07 新增（重叠校验）。返回生成主键。 */
    Long createTimeSlot(TimeSlot slot);

    /** BE-API-08 修改（重叠校验）。 */
    void updateTimeSlot(Long id, TimeSlot slot);

    /** BE-API-09 删除保护：被排课引用时抛 409 并带引用计数。 */
    void deleteTimeSlot(Long id);
}
