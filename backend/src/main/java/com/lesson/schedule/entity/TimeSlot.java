package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

/**
 * 时间段（排课网格的列）。对应表 {@code time_slot}（概设 §2.3）。
 */
@Data
@TableName("time_slot")
public class TimeSlot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 起 TIME */
    private LocalTime startTime;

    /** 止 TIME（应用层校验不与已有时段重叠） */
    private LocalTime endTime;

    /** 排序（网格列序） */
    private Integer sortOrder;

    /** 启用 1/0 */
    private Integer enabled;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
