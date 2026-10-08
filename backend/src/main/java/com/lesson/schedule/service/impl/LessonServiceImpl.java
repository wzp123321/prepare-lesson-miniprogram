package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.dto.BatchLessonDTO;
import com.lesson.schedule.common.dto.LessonQueryDTO;
import com.lesson.schedule.common.vo.BatchLessonResultVO;
import com.lesson.schedule.common.vo.LessonCellVO;
import com.lesson.schedule.common.vo.LessonRecordVO;
import com.lesson.schedule.common.vo.MonthGridVO;
import com.lesson.schedule.common.vo.TodayLessonVO;
import com.lesson.schedule.common.vo.TodayVO;
import com.lesson.schedule.entity.Course;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.entity.Lesson.LessonStatus;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.mapper.CourseMapper;
import com.lesson.schedule.mapper.LessonMapper;
import com.lesson.schedule.mapper.StudentMapper;
import com.lesson.schedule.service.LessonService;
import com.lesson.schedule.service.TimeSlotService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排课服务实现（S04 核心，Wave C）。
 * <p>核心域规则写在 service 层（依 AGENTS 4.2 DDD：核心规则不散落 controller）。</p>
 */
@Service
@RequiredArgsConstructor
public class LessonServiceImpl implements LessonService {

    private final LessonMapper lessonMapper;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final TimeSlotService timeSlotService;

    // ============================ S04 排课核心写接口（Wave G1） ============================

    @Override
    public Long createLesson(Long studentId, Long slotId, LocalDate lessonDate) {
        if (studentId == null || slotId == null || lessonDate == null) {
            throw new BusinessException(400, "studentId / slotId / lessonDate 均不能为空");
        }
        // 历史月写护栏 C-07：仅当前月可排
        if (!isCurrentMonth(lessonDate)) {
            throw new BusinessException(409, "只能排当前月课程：lessonDate=" + lessonDate);
        }
        // 每格仅 1 人：同 (lessonDate, slotId) 已占用 → 409
        Lesson occupied = lessonMapper.selectByDateAndSlot(lessonDate, slotId);
        if (occupied != null) {
            throw new BusinessException(409, "该时段已排课");
        }
        // 解析该生唯一课程（course_id 为 lesson 必填关联）
        Course course = courseMapper.selectByStudentId(studentId);
        if (course == null) {
            throw new BusinessException(400, "学生尚未配置课程，无法排课：studentId=" + studentId);
        }
        Lesson lesson = new Lesson();
        lesson.setStudentId(studentId);
        lesson.setCourseId(course.getId());
        lesson.setSlotId(slotId);
        lesson.setLessonDate(lessonDate);
        lesson.setStatus(LessonStatus.UNTAKEN.name());
        lesson.setClosed(0);
        lessonMapper.insert(lesson);
        return lesson.getId();
    }

    @Override
    @Transactional
    public BatchLessonResultVO batchCreateLessons(BatchLessonDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "批量排课参数不能为空");
        }
        if (dto.getWeekdays() == null || dto.getWeekdays().isEmpty()) {
            throw new BusinessException(400, "请至少选择一个星期");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new BusinessException(400, "startDate / endDate 不能为空");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException(400, "结束日期不能早于开始日期");
        }
        // 前置校验一次：学生课程缺失则整批都排不了，提前报错而非逐条失败
        Course course = courseMapper.selectByStudentId(dto.getStudentId());
        if (course == null) {
            throw new BusinessException(400, "学生尚未配置课程，无法排课：studentId=" + dto.getStudentId());
        }
        BatchLessonResultVO result = new BatchLessonResultVO();
        LocalDate day = dto.getStartDate();
        int guard = 0; // 区间上限保护，避免误填造成海量插入
        while (!day.isAfter(dto.getEndDate()) && guard++ < 366) {
            if (dto.getWeekdays().contains(day.getDayOfWeek().getValue())) {
                if (!isCurrentMonth(day)) {
                    // C-07 历史月写护栏：非当前月跳过（不中断整批）
                    result.setSkipped(result.getSkipped() + 1);
                    result.getSkippedDates().add(day.toString());
                } else if (lessonMapper.selectByDateAndSlot(day, dto.getSlotId()) != null) {
                    // 每格仅 1 人：已占用跳过
                    result.setSkipped(result.getSkipped() + 1);
                    result.getSkippedDates().add(day.toString());
                } else {
                    Lesson lesson = new Lesson();
                    lesson.setStudentId(dto.getStudentId());
                    lesson.setCourseId(course.getId());
                    lesson.setSlotId(dto.getSlotId());
                    lesson.setLessonDate(day);
                    lesson.setStatus(LessonStatus.UNTAKEN.name());
                    lesson.setClosed(0);
                    lessonMapper.insert(lesson);
                    result.setCreated(result.getCreated() + 1);
                }
            }
            day = day.plusDays(1);
        }
        return result;
    }

    @Override
    @Transactional
    public BatchLessonResultVO copyWeek(LocalDate sourceFrom, LocalDate targetFrom) {
        if (sourceFrom == null || targetFrom == null) {
            throw new BusinessException(400, "sourceFrom / targetFrom 不能为空");
        }
        List<Lesson> source = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .ge(Lesson::getLessonDate, sourceFrom)
                .le(Lesson::getLessonDate, sourceFrom.plusDays(6))
                .in(Lesson::getStatus, LessonStatus.UNTAKEN.name(), LessonStatus.NORMAL.name()));
        BatchLessonResultVO result = new BatchLessonResultVO();
        for (Lesson src : source) {
            long offset = ChronoUnit.DAYS.between(sourceFrom, src.getLessonDate());
            LocalDate target = targetFrom.plusDays(offset);
            if (!isCurrentMonth(target)) {
                // C-07 历史月/未来月写护栏：同一套规则，跳过不中断
                result.setSkipped(result.getSkipped() + 1);
                result.getSkippedDates().add(target.toString());
            } else if (lessonMapper.selectByDateAndSlot(target, src.getSlotId()) != null) {
                // 每格仅 1 人：目标格已占用
                result.setSkipped(result.getSkipped() + 1);
                result.getSkippedDates().add(target.toString());
            } else {
                Lesson lesson = new Lesson();
                lesson.setStudentId(src.getStudentId());
                lesson.setCourseId(src.getCourseId());
                lesson.setSlotId(src.getSlotId());
                lesson.setLessonDate(target);
                lesson.setStatus(LessonStatus.UNTAKEN.name());
                lesson.setClosed(0);
                lessonMapper.insert(lesson);
                result.setCreated(result.getCreated() + 1);
            }
        }
        return result;
    }

    @Override
    public void deleteLesson(Long id) {
        Lesson lesson = requireLesson(id);
        // C-07 历史月只读护栏：历史月课次不可删
        assertNotHistoryMonth(lesson);
        lessonMapper.deleteById(id);
    }

    @Override
    public void updateLesson(Long id, Long slotId, LocalDate lessonDate) {
        Lesson lesson = requireLesson(id);
        // C-07 历史月只读护栏
        assertNotHistoryMonth(lesson);
        if (slotId == null || lessonDate == null) {
            throw new BusinessException(400, "slotId / lessonDate 均不能为空");
        }
        // 仅当前月可排
        if (!isCurrentMonth(lessonDate)) {
            throw new BusinessException(409, "只能排当前月课程：lessonDate=" + lessonDate);
        }
        // 每格仅 1 人：排除自身后同 (lessonDate, slotId) 已占用 → 409
        Lesson occupied = lessonMapper.selectByDateAndSlot(lessonDate, slotId);
        if (occupied != null && !occupied.getId().equals(id)) {
            throw new BusinessException(409, "该时段已排课");
        }
        lesson.setSlotId(slotId);
        lesson.setLessonDate(lessonDate);
        lessonMapper.updateById(lesson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveMonth(int year, int month) {
        // 计算「上月」边界：year+month 减一个月
        YearMonth prev = YearMonth.of(year, month).minusMonths(1);
        LocalDate prevStart = prev.atDay(1);
        LocalDate prevEnd = prev.atEndOfMonth();
        // 查询上月 status='ABSENT' AND closed=0 的待补
        List<Lesson> pending = lessonMapper.selectPendingAbsentInRange(prevStart, prevEnd);
        int closedCount = 0;
        if (pending != null && !pending.isEmpty()) {
            List<Long> ids = pending.stream().map(Lesson::getId).collect(Collectors.toList());
            closedCount = lessonMapper.closeBatch(ids);
        }
        return closedCount;
    }

    @Override
    public MonthGridVO getMonthGrid(int year, int month) {
        MonthGridVO vo = new MonthGridVO();
        vo.setYear(year);
        vo.setMonth(month);

        // 当月日期序列（行）
        LocalDate monthStart = LocalDate.of(year, month, 1);
        int length = monthStart.lengthOfMonth();
        List<String> days = new ArrayList<>(length);
        for (int d = 1; d <= length; d++) {
            days.add(monthStart.withDayOfMonth(d).toString());
        }
        vo.setDays(days);

        // 时段序列（列）
        vo.setSlots(timeSlotService.listAll());

        // 单元映射：lessonDate#slotId → 单元
        List<LessonCellVO> cells = lessonMapper.selectMonthGrid(year, month);
        Map<String, LessonCellVO> cellMap = new LinkedHashMap<>();
        if (cells != null) {
            for (LessonCellVO c : cells) {
                cellMap.put(c.getLessonDate() + "#" + c.getSlotId(), c);
            }
        }
        vo.setCells(cellMap);
        return vo;
    }

    // ============================ Wave D：顺延 / 补课（S05/S06） ============================

    @Override
    public void markAbsent(Long id, String absentBy, String reason) {
        Lesson lesson = requireLesson(id);
        // C-07 历史月只读护栏：仅允许修改当前月及以后的课次
        assertNotHistoryMonth(lesson);
        // 仅 UNTAKEN 可标记顺延（状态机：UNTAKEN → ABSENT）
        if (!LessonStatus.UNTAKEN.name().equals(lesson.getStatus())) {
            throw new BusinessException(400, "仅未上课(UNTAKEN)可标记顺延，当前状态：" + lesson.getStatus());
        }
        if (!Lesson.AbsentBy.isValid(absentBy)) {
            throw new BusinessException(400, "请假方(absentBy)非法，应为 student 或 teacher：absentBy=" + absentBy);
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException(400, "顺延原因(absentReason)不能为空");
        }
        lesson.setStatus(LessonStatus.ABSENT.name());
        lesson.setAbsentBy(absentBy);
        lesson.setAbsentReason(reason);
        lesson.setClosed(0); // 入待补
        lessonMapper.updateById(lesson);
    }

    @Override
    public void changeStatus(Long id, String status) {
        Lesson lesson = requireLesson(id);
        assertNotHistoryMonth(lesson);
        // 标记为顺延走专用 /absent 接口
        if (LessonStatus.ABSENT.name().equals(status)) {
            throw new BusinessException(400, "标记为顺延请使用 /api/lessons/{id}/absent 接口");
        }
        if (!LessonStatus.isValid(status)) {
            throw new BusinessException(400, "非法状态值：" + status);
        }
        LessonStatus from = LessonStatus.valueOf(lesson.getStatus());
        LessonStatus to = LessonStatus.valueOf(status);
        if (!isAllowedTransition(from, to)) {
            throw new BusinessException(400, "非法状态迁移：" + from + " → " + to);
        }
        lesson.setStatus(to.name());
        lessonMapper.updateById(lesson);
    }

    @Override
    public void arrangeMakeUp(Long id, LocalDate makeUpDate) {
        Lesson lesson = requireLesson(id);
        // C-07 历史月只读护栏：仅允许修改当前月及以后的课次
        assertNotHistoryMonth(lesson);
        if (!LessonStatus.ABSENT.name().equals(lesson.getStatus())) {
            throw new BusinessException(400, "仅顺延(ABSENT)课次可安排补课，当前状态：" + lesson.getStatus());
        }
        if (lesson.getClosed() != null && lesson.getClosed() == 1) {
            throw new BusinessException(400, "该待补课已关闭，无法再安排补课");
        }
        if (makeUpDate == null) {
            throw new BusinessException(400, "补课日期(makeUpDate)不能为空");
        }
        // 契约 BE-API-24：安排补课即登记「已补」完成态（status=MADEUP、closed=1），可跨月。
        // 此前拆成 arrangeMakeUp(仅写日期) + 契约外 made-up 两步，导致前端无入口、MADEUP 不可达；
        // 现收敛为一次调用，使补课闭环在 BE-API-24 即达成。
        lesson.setMakeUpDate(makeUpDate);
        lesson.setStatus(LessonStatus.MADEUP.name());
        lesson.setClosed(1);
        lessonMapper.updateById(lesson);
    }

    @Override
    public void markMadeUp(Long id) {
        Lesson lesson = requireLesson(id);
        // C-07 历史月只读护栏：仅允许修改当前月及以后的课次
        assertNotHistoryMonth(lesson);
        if (LessonStatus.MADEUP.name().equals(lesson.getStatus())) {
            return; // 幂等
        }
        if (!LessonStatus.ABSENT.name().equals(lesson.getStatus())) {
            throw new BusinessException(400, "仅顺延(ABSENT)课次可标记已补，当前状态：" + lesson.getStatus());
        }
        // 🟡#4 必须先经 arrangeMakeUp 写入补课日期，否则不可直达已补
        if (lesson.getMakeUpDate() == null) {
            throw new BusinessException(400, "请先安排补课日期");
        }
        lesson.setStatus(LessonStatus.MADEUP.name());
        lesson.setClosed(1); // 补课完成，计入已补 Z
        lessonMapper.updateById(lesson);
    }

    @Override
    public void closePending(Long id) {
        Lesson lesson = requireLesson(id);
        // C-07 历史月只读护栏：仅允许修改当前月及以后的课次
        assertNotHistoryMonth(lesson);
        if (!LessonStatus.ABSENT.name().equals(lesson.getStatus())) {
            throw new BusinessException(400, "仅顺延(ABSENT)课次可手动关闭待补，当前状态：" + lesson.getStatus());
        }
        if (lesson.getClosed() != null && lesson.getClosed() == 1) {
            return; // 幂等
        }
        // 仅置 closed=1，status 保持 ABSENT（不计入已补 Z）
        lesson.setClosed(1);
        lessonMapper.updateById(lesson);
    }

    @Override
    public List<Lesson> listPending() {
        List<Lesson> list = lessonMapper.selectPendingMakeUp();
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    public TodayVO getToday() {
        LocalDate today = LocalDate.now();
        TodayVO vo = new TodayVO();
        List<TodayLessonVO> lessons = lessonMapper.selectTodayLessons(today);
        vo.setLessons(lessons == null ? new ArrayList<>() : lessons);
        List<LessonCellVO> pendingToday = lessonMapper.selectPendingByMakeUpDate(today);
        vo.setPendingToday(pendingToday == null ? new ArrayList<>() : pendingToday);
        return vo;
    }

    // ---------------------------- 私有辅助 ----------------------------

    /** 加载课次，不存在抛 404。 */
    private Lesson requireLesson(Long id) {
        Lesson lesson = lessonMapper.selectById(id);
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在：id=" + id);
        }
        return lesson;
    }

    /** C-07 历史月只读护栏：lesson_date 早于当前月（仅年/月比较）的写操作拒绝，返回 409。 */
    private void assertNotHistoryMonth(Lesson lesson) {
        if (lesson.getLessonDate() == null) {
            return;
        }
        YearMonth current = YearMonth.now();
        YearMonth lessonYm = YearMonth.of(lesson.getLessonDate().getYear(),
                lesson.getLessonDate().getMonthValue());
        if (lessonYm.isBefore(current)) {
            throw new BusinessException(409, "历史月课次不可修改：lessonDate=" + lesson.getLessonDate());
        }
    }

    /** 仅排当月约束：lessonDate 的 年/月 必须等于当前 年/月（C-07：历史月/未来月越界均拒绝）。 */
    private boolean isCurrentMonth(LocalDate date) {
        if (date == null) {
            return false;
        }
        YearMonth current = YearMonth.now();
        return current.getYear() == date.getYear() && current.getMonthValue() == date.getMonthValue();
    }

    /** 状态机迁移白名单：UNTAKEN→NORMAL；ABSENT→CANCELLED（ABSENT→MADEUP 仅经 markMadeUp 达成）；其余终态不可迁移。 */
    private boolean isAllowedTransition(LessonStatus from, LessonStatus to) {
        if (from == to) {
            return true; // 幂等
        }
        switch (from) {
            case UNTAKEN:
                return to == LessonStatus.NORMAL; // UNTAKEN→ABSENT 走 markAbsent
            case ABSENT:
                return to == LessonStatus.CANCELLED; // ABSENT→MADEUP 仅经 markMadeUp
            default:
                return false; // NORMAL / MADEUP / CANCELLED 为终态
        }
    }

    @Override
    public PageResult<LessonRecordVO> queryRecords(LessonQueryDTO query) {
        long page = query == null || query.getPage() == null ? 1L : query.getPage();
        long size = query == null || query.getSize() == null ? 20L : query.getSize();

        // 年级不在 lesson 表上：先按 grade 取学生 id；命中 0 人直接返回空页（避免 IN () 语法错误）
        List<Long> gradeStuIds = null;
        if (query != null && query.getGrade() != null && !query.getGrade().isBlank()) {
            gradeStuIds = studentMapper.selectList(Wrappers.<Student>lambdaQuery()
                            .eq(Student::getGrade, query.getGrade()))
                    .stream().map(Student::getId).toList();
            if (gradeStuIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, page, size);
            }
        }

        var qw = Wrappers.<Lesson>lambdaQuery();
        if (query != null) {
            if (query.getStudentId() != null) {
                qw.eq(Lesson::getStudentId, query.getStudentId());
            }
            if (gradeStuIds != null) {
                qw.in(Lesson::getStudentId, gradeStuIds);
            }
            if (query.getFrom() != null) {
                qw.ge(Lesson::getLessonDate, query.getFrom());
            }
            if (query.getTo() != null) {
                qw.le(Lesson::getLessonDate, query.getTo());
            }
            if (query.getStatus() != null && !query.getStatus().isBlank()) {
                qw.eq(Lesson::getStatus, query.getStatus());
            }
        }
        qw.orderByDesc(Lesson::getLessonDate).orderByAsc(Lesson::getSlotId);

        Page<Lesson> p = lessonMapper.selectPage(new Page<>(page, size), qw);
        List<Lesson> rows = p.getRecords();
        if (rows.isEmpty()) {
            return new PageResult<>(List.of(), p.getTotal(), page, size);
        }

        Set<Long> stuIds = new HashSet<>();
        Set<Long> slotIds = new HashSet<>();
        for (Lesson l : rows) {
            if (l.getStudentId() != null) {
                stuIds.add(l.getStudentId());
            }
            if (l.getSlotId() != null) {
                slotIds.add(l.getSlotId());
            }
        }
        Map<Long, Student> stuMap = new HashMap<>();
        if (!stuIds.isEmpty()) {
            for (Student s : studentMapper.selectBatchIds(stuIds)) {
                stuMap.put(s.getId(), s);
            }
        }
        Map<Long, TimeSlot> slotMap = new HashMap<>();
        for (TimeSlot t : timeSlotService.listAll()) {
            if (slotIds.contains(t.getId())) {
                slotMap.put(t.getId(), t);
            }
        }

        List<LessonRecordVO> list = new ArrayList<>();
        for (Lesson l : rows) {
            LessonRecordVO vo = new LessonRecordVO();
            vo.setId(l.getId());
            vo.setLessonDate(l.getLessonDate());
            vo.setWeekday(l.getLessonDate() == null ? null : l.getLessonDate().getDayOfWeek().getValue());
            vo.setSlotId(l.getSlotId());
            TimeSlot slot = l.getSlotId() == null ? null : slotMap.get(l.getSlotId());
            if (slot != null) {
                vo.setSlotStart(slot.getStartTime());
                vo.setSlotEnd(slot.getEndTime());
            }
            vo.setStudentId(l.getStudentId());
            Student stu = l.getStudentId() == null ? null : stuMap.get(l.getStudentId());
            vo.setStudentName(stu == null ? null : stu.getName());
            vo.setGrade(stu == null ? null : stu.getGrade());
            vo.setStatus(l.getStatus());
            vo.setAbsentBy(l.getAbsentBy());
            vo.setAbsentReason(l.getAbsentReason());
            vo.setMakeUpDate(l.getMakeUpDate());
            vo.setClosed(l.getClosed());
            vo.setRemark(l.getRemark());
            list.add(vo);
        }
        return new PageResult<>(list, p.getTotal(), page, size);
    }
}
