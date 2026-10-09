package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lesson.schedule.common.vo.LessonCellVO;
import com.lesson.schedule.common.vo.TodayLessonVO;
import com.lesson.schedule.common.vo.TodayTodoVO;
import com.lesson.schedule.common.vo.TodoItemVO;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.entity.LessonKnowledge;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.mapper.LessonKnowledgeMapper;
import com.lesson.schedule.mapper.LessonMapper;
import com.lesson.schedule.mapper.StudentMapper;
import com.lesson.schedule.service.DashboardService;
import com.lesson.schedule.service.TimeSlotService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 今日待办聚合实现（BE-API-34）。只读，复用 LessonMapper / StudentMapper 既有查询，
 * 不新增表、不新增 Mapper SQL、不改任何状态。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** 未备课口径：这些状态下的课还没上完，备课才有意义 */
    private static final Set<String> PREP_RELEVANT = Set.of("UNTAKEN", "NORMAL");

    private final LessonMapper lessonMapper;
    private final StudentMapper studentMapper;
    private final LessonKnowledgeMapper lessonKnowledgeMapper;
    private final TimeSlotService timeSlotService;

    @Override
    public TodayTodoVO todayTodo() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        Map<Long, Student> stuMap = studentMap();
        Map<Long, String> slotLabels = slotLabelMap();

        List<TodoItemVO> markToday = markToday(today, stuMap, slotLabels);
        List<TodoItemVO> makeUpToday = makeUpOn(today, stuMap, slotLabels, true);
        List<TodoItemVO> makeUpOverdue = makeUpOverdue(today, stuMap, slotLabels);
        List<TodoItemVO> prepToday = prepOn(tomorrow, stuMap, slotLabels);

        TodayTodoVO vo = new TodayTodoVO();
        vo.setToday(today);
        vo.setMarkToday(markToday);
        vo.setMakeUpToday(makeUpToday);
        vo.setMakeUpOverdue(makeUpOverdue);
        vo.setPrepToday(prepToday);
        vo.setTotal(markToday.size() + makeUpToday.size() + makeUpOverdue.size() + prepToday.size());
        return vo;
    }

    /** 今天有课但状态仍是 UNTAKEN —— 上完没上完都该有个说法。 */
    private List<TodoItemVO> markToday(LocalDate today, Map<Long, Student> stuMap,
                                       Map<Long, String> slotLabels) {
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getLessonDate, today)
                .eq(Lesson::getStatus, "UNTAKEN")
                .orderByAsc(Lesson::getSlotId));
        List<TodoItemVO> list = new ArrayList<>();
        for (Lesson l : lessons) {
            TodoItemVO item = base(l, stuMap, slotLabels);
            item.setCategory("MARK_TODAY");
            item.setNote("今天上课，上完记得标记「正常上课」或「顺延」");
            list.add(item);
        }
        return list;
    }

    /** 今天该补：make_up_date = 今天且仍是 ABSENT 未关闭。 */
    private List<TodoItemVO> makeUpOn(LocalDate date, Map<Long, Student> stuMap,
                                      Map<Long, String> slotLabels, boolean isToday) {
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getMakeUpDate, date)
                .eq(Lesson::getStatus, "ABSENT")
                .eq(Lesson::getClosed, 0)
                .orderByAsc(Lesson::getSlotId));
        List<TodoItemVO> list = new ArrayList<>();
        for (Lesson l : lessons) {
            TodoItemVO item = base(l, stuMap, slotLabels);
            item.setCategory("MAKEUP_TODAY");
            item.setNote("今天补课（原顺延日 " + l.getLessonDate() + "），补完点「标记已补」");
            list.add(item);
        }
        return list;
    }

    /**
     * 逾期待补：原顺延日期已过、至今未关闭也没安排补课日的课。
     * <p>「安排补课」会同时写 make_up_date 并转 MADEUP，因此未补的必然 make_up_date 为空；
     * 判断条件写成「makeUpDate 为空」比「日期早于今天」更准，也不会漏掉跨月的。</p>
     */
    private List<TodoItemVO> makeUpOverdue(LocalDate today, Map<Long, Student> stuMap,
                                           Map<Long, String> slotLabels) {
        List<Lesson> pending = lessonMapper.selectPendingMakeUp();
        List<TodoItemVO> list = new ArrayList<>();
        if (pending == null) {
            return list;
        }
        for (Lesson l : pending) {
            if (l.getMakeUpDate() != null) {
                continue; // 已排定补课日 → 归「今天该补」管，不算逾期
            }
            if (l.getLessonDate() == null || !l.getLessonDate().isBefore(today)) {
                continue; // 原课还没到日子，不急
            }
            long overdue = java.time.temporal.ChronoUnit.DAYS.between(l.getLessonDate(), today);
            TodoItemVO item = base(l, stuMap, slotLabels);
            item.setCategory("MAKEUP_OVERDUE");
            item.setNote("已顺延 " + overdue + " 天未安排补课，尽早跟学生约时间");
            list.add(item);
        }
        // 拖得最久的排最前
        list.sort((a, b) -> {
            LocalDate da = a.getLessonDate();
            LocalDate db = b.getLessonDate();
            if (da == null || db == null) {
                return 0;
            }
            return da.compareTo(db);
        });
        return list;
    }

    /** 明天要上但还没配知识点的课（今晚该备课）。 */
    private List<TodoItemVO> prepOn(LocalDate date, Map<Long, Student> stuMap,
                                    Map<Long, String> slotLabels) {
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getLessonDate, date)
                .in(Lesson::getStatus, PREP_RELEVANT)
                .orderByAsc(Lesson::getSlotId));
        if (lessons.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Set<Long> prepared = new HashSet<>();
        for (LessonKnowledge lk : lessonKnowledgeMapper.selectList(Wrappers.<LessonKnowledge>lambdaQuery()
                .in(LessonKnowledge::getLessonId, lessonIds))) {
            prepared.add(lk.getLessonId());
        }
        List<TodoItemVO> list = new ArrayList<>();
        for (Lesson l : lessons) {
            if (prepared.contains(l.getId())) {
                continue; // 已配知识点，不用催
            }
            TodoItemVO item = base(l, stuMap, slotLabels);
            item.setCategory("PREP_TODAY");
            item.setNote("明天上课，还没准备知识点与试卷");
            list.add(item);
        }
        return list;
    }

    // ---------------------------- 私有辅助 ----------------------------

    /** 组装待办基础字段（学生 / 时段 / 请假信息）。 */
    private TodoItemVO base(Lesson l, Map<Long, Student> stuMap, Map<Long, String> slotLabels) {
        TodoItemVO item = new TodoItemVO();
        item.setLessonId(l.getId());
        item.setLessonDate(l.getLessonDate());
        item.setStatus(l.getStatus());
        item.setAbsentBy(l.getAbsentBy());
        item.setAbsentReason(l.getAbsentReason());
        item.setStudentId(l.getStudentId());
        Student stu = l.getStudentId() == null ? null : stuMap.get(l.getStudentId());
        if (stu != null) {
            item.setStudentName(stu.getName());
            item.setGrade(stu.getGrade());
        }
        item.setSlot(l.getSlotId() == null ? null : slotLabels.get(l.getSlotId()));
        return item;
    }

    /** 在读学生 id → 实体（待办只关心在读的，暂停归档的学生不再催）。 */
    private Map<Long, Student> studentMap() {
        Map<Long, Student> map = new HashMap<>();
        for (Student s : studentMapper.selectList(Wrappers.<Student>lambdaQuery()
                .eq(Student::getStatus, 1))) {
            map.put(s.getId(), s);
        }
        return map;
    }

    /** 时段 id → "HH:mm-HH:mm" 标签。 */
    private Map<Long, String> slotLabelMap() {
        Map<Long, String> map = new HashMap<>();
        for (TimeSlot t : timeSlotService.listAll()) {
            LocalTime start = t.getStartTime();
            LocalTime end = t.getEndTime();
            if (start != null && end != null) {
                map.put(t.getId(), HH_MM.format(start) + "-" + HH_MM.format(end));
            }
        }
        return map;
    }
}
