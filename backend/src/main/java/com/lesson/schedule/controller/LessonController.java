package com.lesson.schedule.controller;

import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.AbsentDTO;
import com.lesson.schedule.common.dto.BatchLessonDTO;
import com.lesson.schedule.common.dto.CopyWeekDTO;
import com.lesson.schedule.common.dto.CreateLessonDTO;
import com.lesson.schedule.common.dto.IdDTO;
import com.lesson.schedule.common.dto.LessonQueryDTO;
import com.lesson.schedule.common.dto.MakeUpCloseDTO;
import com.lesson.schedule.common.dto.MakeUpDTO;
import com.lesson.schedule.common.dto.MonthExportDTO;
import com.lesson.schedule.common.dto.SaveMonthDTO;
import com.lesson.schedule.common.dto.StatusChangeDTO;
import com.lesson.schedule.common.dto.StudentScheduleExportDTO;
import com.lesson.schedule.common.dto.UpdateLessonDTO;
import com.lesson.schedule.common.vo.MonthGridVO;
import com.lesson.schedule.common.vo.BatchLessonResultVO;
import com.lesson.schedule.common.vo.LessonRecordVO;
import com.lesson.schedule.common.vo.StudentMonthScheduleVO;
import com.lesson.schedule.common.vo.TodayVO;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.service.LessonExportService;
import com.lesson.schedule.service.LessonService;
import com.lesson.schedule.service.StudentService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 排课管理（S04 核心 + Wave D 顺延/补课）。路径对齐概设 §2.4.4 / §2.4.5 / §2.4.6。
 * <ul>
 *   <li>BE-API-15 当月网格、BE-API-19 保存当月（Wave C）</li>
 *   <li>BE-API-16/17/18 增删改、BE-API-20 出图数据</li>
 *   <li>BE-API-21 标记顺延、BE-API-22 状态切换、BE-API-23 待补列表、BE-API-24 安排补课、BE-API-25 手动关闭（Wave D）</li>
 *   <li>BE-API-33 今日视图（S09）</li>
 * </ul>
 * 统一 POST + 动作化路径；id 类参数改由 {@code @RequestBody} 传入，路径不含变量。
 * 月/年类查询（BE-API-15/20/30~32）保留 {@code @RequestParam}。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;
    private final StudentService studentService;
    private final LessonExportService lessonExportService;

    // ============================ S04 排课核心写接口 ============================

    /** BE-API-16 拖拽建课。body: {studentId, slotId, lessonDate}。同格冲突 409；跨月 409。 */
    @PostMapping("/lessons")
    public Result<Long> createLesson(@Valid @RequestBody CreateLessonDTO dto) {
        Long id = lessonService.createLesson(dto.getStudentId(), dto.getSlotId(), dto.getLessonDate());
        return Result.success(id);
    }

    /**
     * BE-API-16B 批量/循环排课。body: {studentId, slotId, weekdays:[1..7], startDate, endDate}。
     * 命中星期且该格空闲则建课；已占用或非当前月的日期跳过并计入 skipped，不中断整批。
     */
    @PostMapping("/lessons/batch")
    public Result<BatchLessonResultVO> batchCreateLessons(@Valid @RequestBody BatchLessonDTO dto) {
        return Result.success(lessonService.batchCreateLessons(dto));
    }

    /**
     * BE-API-16C 复制某周课表到另一周。body: {sourceFrom, targetFrom}（均为该周周一）。
     * 源周内 未上(UNTAKEN)/正常(NORMAL) 的课按同星期几平移到目标周；
     * 非当前月或目标格已占用则跳过并计入 skipped，不中断整批。与批量排课共用同一套护栏。
     */
    @PostMapping("/lessons/copy-week")
    public Result<BatchLessonResultVO> copyWeek(@Valid @RequestBody CopyWeekDTO dto) {
        return Result.success(lessonService.copyWeek(dto.getSourceFrom(), dto.getTargetFrom()));
    }

    /**
     * BE-API-34 排课记录查询（分页）。body: {studentId?, grade?, from?, to?, status?, page?, size?}。
     * 供「排课记录」页按学生 / 年级 / 时间范围筛选课节；返回 {list, total, page, size}。
     */
    @PostMapping("/lessons/query")
    public Result<PageResult<LessonRecordVO>> queryRecords(@RequestBody LessonQueryDTO dto) {
        return Result.success(lessonService.queryRecords(dto));
    }

    /** BE-API-17 删课（拖出/点删）。历史月课次返回 409（C-07）。body: {id}。 */
    @PostMapping("/lessons/delete")
    public Result<Void> deleteLesson(@RequestBody IdDTO dto) {
        lessonService.deleteLesson(dto.getId());
        return Result.success();
    }

    /** BE-API-18 改课（换时段/日期）。body: {id, slotId, lessonDate}。同格冲突 409；跨月 409。 */
    @PostMapping("/lessons/update")
    public Result<Void> updateLesson(@Valid @RequestBody UpdateLessonDTO dto) {
        lessonService.updateLesson(dto.getId(), dto.getSlotId(), dto.getLessonDate());
        return Result.success();
    }

    /**
     * BE-API-19 保存当月。body: {year, month}，不接收网格变更集：
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
     * 导出当月排课表（xlsx 二进制流，**不走 Result 包装**）。body: {year, month}。
     * <p>两张表：Sheet「排课总表」（日期 × 时段 矩阵）+ Sheet「排课明细」。</p>
     */
    @PostMapping("/lessons/export")
    public void exportMonthExcel(@Valid @RequestBody MonthExportDTO dto, HttpServletResponse response)
            throws IOException {
        String name = "排课表_" + dto.getYear() + "-" + String.format("%02d", dto.getMonth());
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded + ".xlsx");
        lessonExportService.exportMonthExcel(dto.getYear(), dto.getMonth(), response.getOutputStream());
    }

    /**
     * BE-API-15 当月网格数据（前后台复用）。
     * 入参（query）：{@code ?date=YYYY-MM} 或 {@code ?year=&month=}。
     */
    @PostMapping("/lessons/month")
    public Result<MonthGridVO> getMonth(@RequestParam(required = false) String date,
                                        @RequestParam(required = false) Integer year,
                                        @RequestParam(required = false) Integer month) {
        int[] ym = resolveYearMonthFromQuery(date, year, month);
        return Result.success(lessonService.getMonthGrid(ym[0], ym[1]));
    }

    /**
     * BE-API-20 按学生当月课表出图数据（PNG 由前端 html2canvas 完成，后端只给数据）。
     * 入参（body）：{studentId, year, month}。
     */
    @PostMapping("/lessons/student-export")
    public Result<StudentMonthScheduleVO> exportStudentMonthSchedule(
            @RequestBody StudentScheduleExportDTO dto) {
        return Result.success(studentService.getStudentMonthSchedule(dto.getStudentId(), dto.getYear(), dto.getMonth()));
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

    // ============================ Wave D：顺延 / 补课 ============================

    /** D-01 / BE-API-21 标记顺延。body: {id, absentBy, absentReason}（必填，缺失 @Valid 拦截为 400）。 */
    @PostMapping("/lessons/absent")
    public Result<Void> markAbsent(@Valid @RequestBody AbsentDTO dto) {
        lessonService.markAbsent(dto.getId(), dto.getAbsentBy(), dto.getAbsentReason());
        return Result.success();
    }

    /** D-02 / BE-API-22 状态切换。body: {id, status}（NORMAL / MADEUP / CANCELLED；标记顺延走 /absent）。 */
    @PostMapping("/lessons/status")
    public Result<Void> changeStatus(@Valid @RequestBody StatusChangeDTO dto) {
        lessonService.changeStatus(dto.getId(), dto.getStatus());
        return Result.success();
    }

    /** D-03 安排补课（写补课日期，status 保持 ABSENT 表示「已约」）。body: {id, makeUpDate}（可跨月）。 */
    @PostMapping("/lessons/make-up")
    public Result<Void> arrangeMakeUp(@Valid @RequestBody MakeUpDTO dto) {
        lessonService.arrangeMakeUp(dto.getId(), dto.getMakeUpDate());
        return Result.success();
    }

    /** BE-API-25 手动关闭待补（首页「已安排进本月课程」）。仅置 closed=1，status 保持 ABSENT（不计入已补 Z）。body: {lessonId}。 */
    @PostMapping("/make-up/close")
    public Result<Void> closePending(@RequestBody MakeUpCloseDTO dto) {
        lessonService.closePending(dto.getLessonId());
        return Result.success();
    }

    /** BE-API-23 待补列表（首页实时）。全局 status=ABSENT AND closed=0，按 lesson_date 升序；无则空数组。 */
    @PostMapping("/make-up/pending")
    public Result<List<Lesson>> listPending() {
        return Result.success(lessonService.listPending());
    }

    // ============================ S09 今日视图 ============================

    /** BE-API-33 今日课程 + 待补提示。返回今日课程（含学生姓名/科目/时段）+ pendingToday（make_up_date=今天的待补课）。 */
    @PostMapping("/lessons/today")
    public Result<TodayVO> today() {
        return Result.success(lessonService.getToday());
    }
}
