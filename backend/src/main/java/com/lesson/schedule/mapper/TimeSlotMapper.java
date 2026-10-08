package com.lesson.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lesson.schedule.entity.TimeSlot;
import java.time.LocalTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 时间段 Mapper（S02）。基础 CRUD 由 MyBatis-Plus 提供，
 * 补充「按开始时间列表」与「重叠区间查询辅助」（A-13）。
 */
public interface TimeSlotMapper extends BaseMapper<TimeSlot> {

    /** 按 start_time 升序返回全部时段（顺序由起止时间决定，不做手工排序）。 */
    List<TimeSlot> listOrdered();

    /**
     * 重叠校验辅助：返回与 [startTime, endTime) 交叉且启用的时段。
     * excludeId 用于「修改自身时排除自己」的边界判断（传 null 表示不排除）。
     */
    List<TimeSlot> selectOverlapping(@Param("startTime") LocalTime startTime,
                                     @Param("endTime") LocalTime endTime,
                                     @Param("excludeId") Long excludeId);
}
