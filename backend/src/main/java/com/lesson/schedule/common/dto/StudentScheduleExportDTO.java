package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 按学生出图数据请求体（BE-API-20）。PNG 由前端 html2canvas 完成，后端只给数据。
 */
@Data
public class StudentScheduleExportDTO {

    /** 学生主键 */
    private Long studentId;

    /** 年（数字） */
    private int year;

    /** 月（数字，1-12） */
    private int month;
}
