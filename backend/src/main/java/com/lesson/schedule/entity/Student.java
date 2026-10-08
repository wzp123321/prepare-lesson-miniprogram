package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 学生（聚合根）。对应表 {@code student}（概设 §2.3）。
 */
@Data
@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 姓名（必填） */
    private String name;

    /** 年级（取自 dict.grade） */
    private String grade;

    /** 电话（选填） */
    private String phone;

    /** 家长微信（选填） */
    private String parentWechat;

    /** 家庭地址（必填，上门/路线参考） */
    private String address;

    /** 课程价格 DECIMAL(10,2) 元/节，月收入统计用 */
    private BigDecimal price;

    /** 在读状态 1 在读 / 0 暂停 */
    private Integer status;

    /** 专属颜色 hex，课表着色 */
    private String color;

    /** 备注（选填） */
    private String remark;

    /** 待补课数（非持久化，详情接口联 lesson 计 ABSENT&closed=0，不映射数据库列） */
    @TableField(exist = false)
    private Integer pendingMakeUpCount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
