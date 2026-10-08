package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/**
 * 排课记录（课节）列表行：日期 / 时段 / 学生 / 年级 / 状态 / 请假 / 补课。
 * <p>供「排课记录」页按学生、年级、时间范围查询展示。</p>
 */
@Data
public class LessonRecordVO {

    private Long id;

    private LocalDate lessonDate;

    /** 星期：1=周一 … 7=周日 */
    private Integer weekday;

    private Long slotId;

    private LocalTime slotStart;

    private LocalTime slotEnd;

    private Long studentId;

    private String studentName;

    /** 年级，来自 student.grade（学生未填则为空） */
    private String grade;

    private String status;

    private String absentBy;

    private String absentReason;

    private LocalDate makeUpDate;

    /** 待补关闭 0/1 */
    private Integer closed;

    /** 排课备注 */
    private String remark;
}
