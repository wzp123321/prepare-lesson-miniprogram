package com.lesson.schedule.service;

import com.lesson.schedule.common.vo.MonthGridVO;
import com.lesson.schedule.common.vo.TodayVO;
import com.lesson.schedule.entity.Lesson;
import java.time.LocalDate;
import java.util.List;

/**
 * 排课服务（S04 核心 + Wave D 顺延/补课）。承载核心域规则：
 * <ul>
 *   <li>保存当月（事务）：删旧 UNTAKEN + 批量插入新排课 + 关闭上月顺延（C-06）</li>
 *   <li>每格仅 1 人冲突校验（(lesson_date, slot_id) 唯一）</li>
 *   <li>只排当月约束</li>
 *   <li>当月网格查询（前后台复用）</li>
 *   <li>标记顺延 / 状态机切换 / 安排补课 / 标记已补 / 手动关闭待补 / 待补列表（Wave D，S05/S06）</li>
 * </ul>
 * 核心域规则写在 service 层（依 AGENTS 4.2 DDD：核心规则不散落 controller）。
 */
public interface LessonService {

    /**
     * BE-API-16 拖拽建课（核心写接口，Wave G1）。
     * <p>规则：① lessonDate 必须属当前月，否则 409（C-07 历史月写护栏）；② (lessonDate, slotId) 已存在返回 409；
     * ③ 按 studentId 解析其唯一课程 courseId；④ 落库 lesson（status=UNTAKEN, closed=0）。</p>
     *
     * @param studentId   学生 id
     * @param slotId      时间段 id
     * @param lessonDate  上课日期（须属当前月）
     * @return 新建 lesson 主键
     */
    Long createLesson(Long studentId, Long slotId, LocalDate lessonDate);

    /**
     * BE-API-17 删课（拖出/点删，Wave G1）。历史月课次返回 409（C-07 历史月只读护栏）。
     *
     * @param id 课次 id
     */
    void deleteLesson(Long id);

    /**
     * BE-API-18 改课（换时段/日期，Wave G1）。
     * <p>规则：① lessonDate 必须属当前月，否则 409（C-07）；② 同 (lessonDate, slotId) 已存在（且非自身）返回 409；
     * courseId 保持不变（同一门课）。</p>
     *
     * @param id          课次 id
     * @param slotId      新时间段 id
     * @param lessonDate  新上课日期（须属当前月）
     */
    void updateLesson(Long id, Long slotId, LocalDate lessonDate);

    /**
     * BE-API-19 保存当月（按用户决策改版，事务）。
     * <p>前端已通过 BE-API-16/17/18 逐条落库，此处仅做「关闭上月顺延」：查询 lesson_date 属<b>上月</b>且
     * status='ABSENT' AND closed=0 的记录，批量置 closed=1（视作已安排进本月，不计入已补 Z），返回关闭条数。</p>
     *
     * @param year  目标年（决定「上月」边界）
     * @param month 目标月
     * @return 关闭的上月顺延条数
     */
    int saveMonth(int year, int month);

    /**
     * BE-API-15 当月网格数据（按日期+时段聚合）。
     *
     * @param year  目标年
     * @param month 目标月
     * @return 网格视图（days/slots/cells）
     */
    MonthGridVO getMonthGrid(int year, int month);

    /**
     * D-01 / BE-API-21 标记顺延。仅允许从 {@code UNTAKEN} 迁移至 {@code ABSENT}。
     * <p>必填 absentBy(student/teacher) 与 absentReason，缺失或不合法抛出 400；
     * 结果置 status=ABSENT、absentBy、absentReason、closed=0（入待补）。</p>
     *
     * @param id        课次 id
     * @param absentBy  请假方 student/teacher
     * @param reason    顺延原因（必填）
     */
    void markAbsent(Long id, String absentBy, String reason);

    /**
     * D-02 / BE-API-22 状态机切换（严格）。
     * <p>合法迁移：UNTAKEN→NORMAL；ABSENT→CANCELLED（NORMAL、MADEUP、CANCELLED 为终态不可再迁移）。
     * MADEUP 仅经 {@code markMadeUp} 达成，不可经本接口直达；status=ABSENT 须走 {@code markAbsent}；
     * 非法迁移或非法值抛出 400。</p>
     *
     * @param id     课次 id
     * @param status 目标状态 NORMAL / CANCELLED（MADEUP 不可经本接口）
     */
    void changeStatus(Long id, String status);

    /**
     * D-03 安排补课（写补课日期，status 保持 ABSENT 表示「已约」）。
     * <p>仅允许对 status=ABSENT 且 closed=0 的待补课调用；makeUpDate 可跨月、必填，缺失抛 400。</p>
     *
     * @param id          课次 id
     * @param makeUpDate  补课日期（可跨月）
     */
    void arrangeMakeUp(Long id, LocalDate makeUpDate);

    /**
     * D-04 标记已补（补课完成）。仅允许从 ABSENT 迁移至 MADEUP，置 closed=1（计入已补 Z）。
     * <p>要求已先经 {@code arrangeMakeUp} 写入补课日期（makeUpDate 非空），否则抛出 400「请先安排补课日期」。</p>
     *
     * @param id 课次 id
     */
    void markMadeUp(Long id);

    /**
     * D-05 手动关闭待补（首页「已安排进本月课程」）。仅置 closed=1，status 保持 ABSENT（不计入已补 Z）。
     * <p>仅允许对 status=ABSENT 的课次调用。</p>
     *
     * @param id 课次 id
     */
    void closePending(Long id);

    /**
     * D-03 / BE-API-23 待补列表（首页实时）。全局查询 status=ABSENT AND closed=0，按 lesson_date 升序。
     * <p>无结果返回空集合（前端据空数组隐藏待补区）。</p>
     *
     * @return 待补课列表（可能为空列表，非 null）
     */
    List<Lesson> listPending();

    /**
     * BE-API-33 今日视图（S09）。返回今日课程（lesson_date = 今天，含学生姓名/课程科目/时段起止）+ pendingToday
     * （待补课中 make_up_date = 今天者，status=ABSENT AND closed=0）。空数据返 [] 而非 null。
     *
     * @return 今日视图
     */
    TodayVO getToday();
}
