package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 字典数据（年级 / 顺延原因预设）。对应表 {@code dict}（概设 §2.3）。
 */
@Data
@TableName("dict")
public class Dict {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型 grade / absent_reason */
    private String dictType;

    /** 字典值（如「小一」「学生病假」） */
    private String dictValue;

    /** 排序 */
    private Integer sortOrder;

    /** 启用 1/0 */
    private Integer enabled;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
