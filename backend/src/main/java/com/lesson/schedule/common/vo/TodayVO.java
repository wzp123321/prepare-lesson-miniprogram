package com.lesson.schedule.common.vo;

import java.util.List;
import lombok.Data;

/**
 * 今日视图（BE-API-33 出参）。
 * <ul>
 *   <li>lessons：今日课程（lesson_date = 今天），含学生姓名 / 课程科目 / 时段起止</li>
 *   <li>pendingToday：待补课中 make_up_date = 今天者（status=ABSENT AND closed=0），即「今日该补」</li>
 * </ul>
 * 空数据返 [] 而非 null。
 */
@Data
public class TodayVO {

    /** 今日课程列表 */
    private List<TodayLessonVO> lessons;

    /** 今日该补的待补列表（make_up_date = 今天） */
    private List<LessonCellVO> pendingToday;
}
