package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 排课/上课记录（核心聚合根）。对应表 {@code lesson}（概设 §2.3、状态机 §2.2）。
 */
@Data
@TableName("lesson")
public class Lesson {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生 */
    private Long studentId;

    /** 课程 */
    private Long courseId;

    /** 时间段（网格列） */
    private Long slotId;

    /** 上课日期 DATE，仅当月可排 */
    private LocalDate lessonDate;

    /** 状态：LessonStatus 枚举值（字符串存储） */
    private String status;

    /** 请假方 AbsentBy 枚举值：student / teacher（仅 ABSENT 时有效） */
    private String absentBy;

    /** 顺延原因（ABSENT 时必填） */
    private String absentReason;

    /** 补课日期 DATE，可跨月，不占新格（仅 MADEUP 时承载） */
    private LocalDate makeUpDate;

    /** 待补关闭 0 未关闭 / 1 已关闭（与 status 正交） */
    private Integer closed;

    /** 备注（选填） */
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 排课状态机枚举（概设 §2.2）。
     * <p>status 字段以字符串存储，业务层引用本枚举值做校验与迁移。</p>
     */
    public enum LessonStatus {
        UNTAKEN,  // 未上
        NORMAL,   // 正常上课
        ABSENT,   // 顺延
        MADEUP,   // 已补
        CANCELLED;// 作废

        public static boolean isValid(String value) {
            if (value == null) {
                return false;
            }
            try {
                valueOf(value);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    /**
     * 请假方枚举（absent_by 取值）。
     */
    public enum AbsentBy {
        student,  // 学生请假
        teacher;  // 老师请假

        public static boolean isValid(String value) {
            if (value == null) {
                return false;
            }
            try {
                valueOf(value);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
