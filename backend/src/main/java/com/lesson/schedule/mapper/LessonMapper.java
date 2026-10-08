package com.lesson.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lesson.schedule.common.vo.LessonCellVO;
import com.lesson.schedule.common.vo.StudentLessonCellVO;
import com.lesson.schedule.common.vo.TodayLessonVO;
import com.lesson.schedule.common.vo.UnscheduledVO;
import com.lesson.schedule.entity.Lesson;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 排课/上课记录 Mapper（S04~S10 核心）。基础 CRUD 由 MyBatis-Plus 提供，
 * 补充 Wave A 需要的若干自定义查询（A-15）：
 * <ul>
 *   <li>同 (lesson_date, slot_id) 冲突查询</li>
 *   <li>按 student_id + 年/月 查询</li>
 *   <li>待补列表查询（status=ABSENT AND closed=0，全局实时）</li>
 *   <li>指定月范围 待补顺延查询（保存排课关闭上月顺延用）</li>
 *   <li>待补批量关闭（仅置 closed=1）</li>
 *   <li>删除保护 / 引用计数查询</li>
 * </ul>
 */
public interface LessonMapper extends BaseMapper<Lesson> {

    /** 同 (lesson_date, slot_id) 是否已被占用（每格 1 人冲突校验）。 */
    Lesson selectByDateAndSlot(@Param("lessonDate") LocalDate lessonDate,
                               @Param("slotId") Long slotId);

    /** 某学生某年某月的全部课（出图 / 统计用）。 */
    List<Lesson> selectByStudentAndMonth(@Param("studentId") Long studentId,
                                         @Param("year") int year,
                                         @Param("month") int month);

    /** 全局待补列表：status=ABSENT AND closed=0（无则返回空集合）。 */
    List<Lesson> selectPendingMakeUp();

    /** 指定日期范围内未关闭的顺延课（保存排课关闭上月顺延用）。 */
    List<Lesson> selectPendingAbsentInRange(@Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    /** 批量关闭待补（仅置 closed=1，status 保持 ABSENT）。 */
    int closeBatch(@Param("ids") List<Long> ids);

    /** 引用计数：该学生是否存在排课（删除保护）。 */
    int countByStudentId(@Param("studentId") Long studentId);

    /** 引用计数：该时间段是否被引用（删除保护）。 */
    int countBySlotId(@Param("slotId") Long slotId);

    /** 引用计数：该课程是否被引用（删除保护）。 */
    int countByCourseId(@Param("courseId") Long courseId);

    /** 当月网格数据：join student 取姓名/颜色，按日期+时段聚合。 */
    List<LessonCellVO> selectMonthGrid(@Param("year") int year, @Param("month") int month);

    /** 今日课程：lesson_date = 指定日期，join student/course/time_slot 取姓名/科目/时段起止（BE-API-33）。 */
    List<TodayLessonVO> selectTodayLessons(@Param("date") LocalDate date);

    /** 今日该补的待补课：status=ABSENT AND closed=0 AND make_up_date = 指定日期（BE-API-33）。 */
    List<LessonCellVO> selectPendingByMakeUpDate(@Param("date") LocalDate date);

    /** 某学生某年某月课表（join time_slot 取起止时间，出图数据 BE-API-20）。 */
    List<StudentLessonCellVO> selectStudentMonthSchedule(@Param("studentId") Long studentId,
                                                         @Param("year") int year,
                                                         @Param("month") int month);

    /** 指定年/月全部课次（统计聚合用，Wave E / S08）。 */
    List<Lesson> selectByMonth(@Param("year") int year, @Param("month") int month);

    /** 指定年/月「未上课」明细（status=ABSENT 或 CANCELLED），join student + time_slot（Wave E / BE-API-31）。 */
    List<UnscheduledVO> selectMonthUnscheduled(@Param("year") int year, @Param("month") int month);
}
