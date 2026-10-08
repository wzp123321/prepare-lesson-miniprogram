package com.lesson.schedule.controller;

import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.AbsentDTO;
import com.lesson.schedule.common.dto.CreateLessonDTO;
import com.lesson.schedule.common.dto.MakeUpDTO;
import com.lesson.schedule.common.dto.SaveMonthDTO;
import com.lesson.schedule.common.dto.StatusChangeDTO;
import com.lesson.schedule.common.dto.UpdateLessonDTO;
import com.lesson.schedule.common.vo.MonthGridVO;
import com.lesson.schedule.common.vo.StudentMonthScheduleVO;
import com.lesson.schedule.common.vo.TodayVO;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.service.LessonService;
import com.lesson.schedule.service.StudentService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 排课管理（S04 核心 + Wave D 顺延/补课）。路径对齐概设 §2.4.4 / §2.4.5 / §2.4.6。
 * <ul>
 *   <li>BE-API-15 当月网格、BE-API-19 保存当月（Wave C）</li>
 *   <li>BE-API-21 标记顺延、BE-API-22 状态切换、BE-API-23 待补列表、BE-API-24 安排补课、BE-API-25 手动关闭（Wave D）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;
    private final StudentService studentService;

    // ============================ S04 排课核心写接口（Wave G1） ============================

    /**
     * BE-API-16 拖拽建课。body: {studentId, slotId, lessonDate}。
     * 同 (lessonDate, slotId) 已存在返回 409；lessonDate 非当前月返回 409。
     */
    @PostMapping("/lessons")
    public Result<Long> createLesson(@Valid @RequestBody CreateLessonDTO dto) {
        Long id = lessonService.createLesson(dto.getStudentId(), dto.getSlotId(), dto.getLessonDate());
        return Result.success(id);
    }

    /**
     * BE-API-17 删课（拖出/点删）。历史月课次返回 409（C-07）。
     */
    @DeleteMapping("/lessons/{id}")
    public Result<Void> deleteLesson(@PathVariable Long id) {
        lessonService.deleteLesson(id);
        return Result.success();
    }

    /**
     * BE-API-18 改课（换时段/日期）。body: {slotId, lessonDate}。
     * 同格冲突 409；lessonDate 非当前月返回 409。
     */
    @PutMapping("/lessons/{id}")
    public Result<Void> updateLesson(@PathVariable Long id,
                                     @Valid @RequestBody UpdateLessonDTO dto) {
        lessonService.updateLesson(id, dto.getSlotId(), dto.getLessonDate());
        return Result.success();
    }

    /**
     * BE-API-19 保存当月（按用户决策改版）。仅接收 {year, month}，不接收网格变更集：
     * 假定 BE-API-16/17/18 已逐条落库，此处仅关闭「上月」顺延（status=ABSENT AND closed=0）并返回 closedCount。
     */
    @PostMapping("/lessons/save-month")
    public Result<Map<String, Integer>> saveMonth(@Valid @RequestBody SaveMonthDTO dto) {
        int closedCount = lessonService.saveMonth(dto.getYear(), dto.getMonth());
        Map<String, Integer> data = new HashMap<>(2);
        data.put("closedCount", closedCount);
        return Result.success(data);
    }

    /**
     * BE-API-15 当月网格数据（前后台复用）。
     * 支持 {@code ?date=YYYY-MM} 或 {@code ?year=&month=} 两种入参。
     */
    @GetMapping("/lessons/month")
    public Result<MonthGridVO> getMonth(@RequestParam(required = false) String date,
                                        @RequestParam(required = false) Integer year,
                                        @RequestParam(required = false) Integer month) {
        int[] ym = resolveYearMonthFromQuery(date, year, month);
        return Result.success(lessonService.getMonthGrid(ym[0], ym[1]));
    }

    /**
     * BE-API-20 按学生当月课表出图数据（PNG 由前端 html2canvas 完成，后端只给数据）。
     * 入参：{@code ?year=&month=}（month 为数字，与 BE-API-15 同款）。
     */
    @GetMapping("/lessons/student/{studentId}/export")
    public Result<StudentMonthScheduleVO> exportStudentMonthSchedule(
            @PathVariable Long studentId,
            @RequestParam int year,
            @RequestParam int month) {
        return Result.success(studentService.getStudentMonthSchedule(studentId, year, month));
    }

    /** 从 query 解析目标年/月：优先 date（YYYY-MM），否则 year+month，再否则 400。 */
    private int[] resolveYearMonthFromQuery(String date, Integer year, Integer month) {
        if (date != null && !date.isEmpty()) {
            YearMonth ym = YearMonth.parse(date);
            return new int[]{ym.getYear(), ym.getMonthValue()};
        }
        if (year != null && month != null) {
            return new int[]{year, month};
        }
        throw new BusinessException(400, "缺少月份参数：请传 date=YYYY-MM 或 year+month");
    }

    // ============================ Wave D：顺延 / 补课（S05/S06） ============================

    /**
     * D-01 / BE-API-21 标记顺延。body: {absentBy, absentReason}（必填，缺失 @Valid 拦截为 400）。
     */
    @PutMapping("/lessons/{id}/absent")
    public Result<Void> markAbsent(@PathVariable Long id,
                                   @Valid @RequestBody AbsentDTO dto) {
        lessonService.markAbsent(id, dto.getAbsentBy(), dto.getAbsentReason());
        return Result.success();
    }

    /**
     * D-02 / BE-API-22 状态切换。body: {status}（NORMAL / MADEUP / CANCELLED；标记顺延走 /absent）。
     */
    @PutMapping("/lessons/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody StatusChangeDTO dto) {
        lessonService.changeStatus(id, dto.getStatus());
        return Result.success();
    }

    /**
     * D-03 安排补课（写补课日期，status 保持 ABSENT 表示「已约」）。body: {makeUpDate}（可跨月）。
     */
    @PutMapping("/lessons/{id}/make-up")
    public Result<Void> arrangeMakeUp(@PathVariable Long id,
                                      @Valid @RequestBody MakeUpDTO dto) {
        lessonService.arrangeMakeUp(id, dto.getMakeUpDate());
        return Result.success();
    }

    /**
     * D-04 标记已补（补课完成）。status→MADEUP 且 closed=1（计入已补 Z）。
     */
    @PutMapping("/lessons/{id}/made-up")
    public Result<Void> markMadeUp(@PathVariable Long id) {
        lessonService.markMadeUp(id);
        return Result.success();
    }

    /**
     * BE-API-25 手动关闭待补（首页「已安排进本月课程」）。路径变量用 lessonId 以契契约。
     * 仅置 closed=1，status 保持 ABSENT（不计入已补 Z）。
     */
    @PutMapping("/make-up/{lessonId}/close")
    public Result<Void> closePending(@PathVariable Long lessonId) {
        lessonService.closePending(lessonId);
        return Result.success();
    }

    /**
     * BE-API-23 待补列表（首页实时）。全局 status=ABSENT AND closed=0，按 lesson_date 升序；无则空数组。
     */
    @GetMapping("/make-up/pending")
    public Result<List<Lesson>> listPending() {
        return Result.success(lessonService.listPending());
    }

    // ============================ S09 今日视图（Wave G1） ============================

    /**
     * BE-API-33 今日课程 + 待补提示。
     * 返回今日课程（lesson_date = 今天，含学生姓名/科目/时段）+ pendingToday（make_up_date = 今天的待补课）。
     */
    @GetMapping("/lessons/today")
    public Result<TodayVO> today() {
        return Result.success(lessonService.getToday());
    }
}
