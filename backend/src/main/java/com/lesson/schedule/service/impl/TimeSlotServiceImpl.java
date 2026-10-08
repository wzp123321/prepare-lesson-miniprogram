package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.mapper.LessonMapper;
import com.lesson.schedule.mapper.TimeSlotMapper;
import com.lesson.schedule.service.TimeSlotService;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimeSlotServiceImpl implements TimeSlotService {

    private final TimeSlotMapper timeSlotMapper;
    private final LessonMapper lessonMapper;

    @Override
    public List<TimeSlot> listAll() {
        return timeSlotMapper.listOrdered();
    }

    @Override
    public Long createTimeSlot(TimeSlot slot) {
        validateInterval(slot);
        checkOverlap(slot.getStartTime(), slot.getEndTime(), null);
        if (slot.getEnabled() == null) {
            slot.setEnabled(1);
        }
        slot.setId(null);
        timeSlotMapper.insert(slot);
        return slot.getId();
    }

    @Override
    public void updateTimeSlot(Long id, TimeSlot slot) {
        TimeSlot existing = timeSlotMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "时间段不存在");
        }
        validateInterval(slot);
        checkOverlap(slot.getStartTime(), slot.getEndTime(), id);
        applyPartial(existing, slot);
        timeSlotMapper.updateById(existing);
    }

    @Override
    public void deleteTimeSlot(Long id) {
        TimeSlot existing = timeSlotMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "时间段不存在");
        }
        int refCount = lessonMapper.countBySlotId(id);
        if (refCount > 0) {
            throw new BusinessException(409, "该时间段已被排课引用，引用数：" + refCount);
        }
        timeSlotMapper.deleteById(id);
    }

    /** 校验起止时间非空且 start < end。 */
    private void validateInterval(TimeSlot slot) {
        if (slot.getStartTime() == null || slot.getEndTime() == null) {
            throw new BusinessException(400, "起止时间不能为空");
        }
        if (!slot.getStartTime().isBefore(slot.getEndTime())) {
            throw new BusinessException(400, "开始时间须早于结束时间");
        }
    }

    /**
     * 重叠校验（域规则，S02）：与「已启用」时段区间 [start,end) 交叉即冲突。
     * 使用 LocalTime.isBefore/isAfter 做半开区间判断：a.start < b.end && a.end > b.start。
     * excludeId 用于修改自身时排除自己。
     */
    private void checkOverlap(LocalTime start, LocalTime end, Long excludeId) {
        List<TimeSlot> enabled = timeSlotMapper.selectList(
                Wrappers.<TimeSlot>lambdaQuery().eq(TimeSlot::getEnabled, 1));
        for (TimeSlot ts : enabled) {
            if (excludeId != null && ts.getId().equals(excludeId)) {
                continue;
            }
            boolean overlap = start.isBefore(ts.getEndTime()) && end.isAfter(ts.getStartTime());
            if (overlap) {
                throw new BusinessException(409, "时间段重叠");
            }
        }
    }

    /** 部分字段更新：仅覆盖请求中非 null 的字段。 */
    private void applyPartial(TimeSlot target, TimeSlot src) {
        if (src.getStartTime() != null) {
            target.setStartTime(src.getStartTime());
        }
        if (src.getEndTime() != null) {
            target.setEndTime(src.getEndTime());
        }
        if (src.getSortOrder() != null) {
            target.setSortOrder(src.getSortOrder());
        }
        if (src.getEnabled() != null) {
            target.setEnabled(src.getEnabled());
        }
    }
}
