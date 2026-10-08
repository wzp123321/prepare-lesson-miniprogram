package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 课程（每个学生一门语文课，挂在 Student 下）。对应表 {@code course}（概设 §2.3）。
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属学生（唯一，每生一门） */
    private Long studentId;

    /** 科目（固定「语文」） */
    private String subject;

    /** 价格（冗余自 student.price 快照） */
    private BigDecimal price;

    /** 备注（选填） */
    private String remark;

    /** 启用 1/0（停用保留历史） */
    private Integer enabled;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
