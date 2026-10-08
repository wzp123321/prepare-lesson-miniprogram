package com.lesson.schedule.common.vo;

import java.util.List;
import lombok.Data;

/**
 * 按学生当月课表出图数据（BE-API-20）。
 * <ul>
 *   <li>studentInfo：学生基础信息（name/color/grade/price）</li>
 *   <li>lessons：该生当月课表列表（join time_slot 取起止时间）</li>
 * </ul>
 * <p>PNG 生成由前端 html2canvas 完成，后端只提供数据（Q4 / PRD 8）。</p>
 */
@Data
public class StudentMonthScheduleVO {

    /** 学生基础信息 */
    private StudentInfoVO studentInfo;

    /** 该生当月课表列表（按 date + slot 升序） */
    private List<StudentLessonCellVO> lessons;
}
