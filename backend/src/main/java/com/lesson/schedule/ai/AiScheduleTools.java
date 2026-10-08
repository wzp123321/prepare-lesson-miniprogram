package com.lesson.schedule.ai;

import com.lesson.schedule.common.vo.LessonCellVO;
import com.lesson.schedule.common.vo.MonthGridVO;
import com.lesson.schedule.common.vo.MonthIncomeVO;
import com.lesson.schedule.common.vo.MonthStatisticVO;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.service.LessonService;
import com.lesson.schedule.service.StatisticsService;
import com.lesson.schedule.service.StudentService;
import com.lesson.schedule.service.TimeSlotService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 智能排课的**只读工具集**：由模型自主调用，直接拿到「算好的」数据，避免它自己瞎算。
 * <p>这些工具不写库，因此不需要老师确认；写操作走 AiActionDTO 方案 + 确认执行。</p>
 * <p>返回中文文本而非对象：模型对文本的引用最稳，也不会因为字段缺失而幻觉。</p>
 */
@Component
@RequiredArgsConstructor
public class AiScheduleTools {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final LessonService lessonService;
    private final StatisticsService statisticsService;
    private final StudentService studentService;
    private final TimeSlotService timeSlotService;

    @Tool(description = "查询指定年月（如 year=2026, month=10）里还没排任何课的在读学生，用于提醒漏排")
    public String unscheduledStudents(int year, int month) {
        Set<Long> scheduled = new HashSet<>();
        MonthGridVO grid = lessonService.getMonthGrid(year, month);
        if (grid.getCells() != null) {
            for (LessonCellVO c : grid.getCells().values()) {
                if (c.getStudentId() != null) {
                    scheduled.add(c.getStudentId());
                }
            }
        }
        List<String> names = new ArrayList<>();
        for (Student s : allStudents()) {
            if (s.getStatus() != null && s.getStatus() == 1 && !scheduled.contains(s.getId())) {
                names.add(s.getName());
            }
        }
        if (names.isEmpty()) {
            return year + "年" + month + "月：没有漏排，所有在读学生本月都有课。";
        }
        return year + "年" + month + "月尚未排课的在读学生（" + names.size() + " 人）："
                + String.join("、", names);
    }

    @Tool(description = "查询某一天（date 格式 yyyy-MM-dd）各个时段的占用情况，用于判断哪里还有空位")
    public String freeSlots(String date) {
        LocalDate d = LocalDate.parse(date);
        MonthGridVO grid = lessonService.getMonthGrid(d.getYear(), d.getMonthValue());
        StringBuilder sb = new StringBuilder();
        sb.append(date).append(' ').append(weekdayCN(d)).append(" 的时段占用情况：");
        int free = 0;
        for (TimeSlot t : timeSlotService.listAll()) {
            LessonCellVO c = grid.getCells() == null ? null : grid.getCells().get(date + "#" + t.getId());
            if (c == null) {
                sb.append("\n  ").append(slotLabel(t)).append("：空闲");
                free++;
            } else {
                sb.append("\n  ").append(slotLabel(t)).append("：已被「")
                        .append(c.getStudentName()).append("」占用（课次ID=").append(c.getId()).append("）");
            }
        }
        sb.append("\n合计空闲 ").append(free).append(" 个时段。");
        return sb.toString();
    }

    @Tool(description = "按学生姓名查询某日期区间（from / to 格式 yyyy-MM-dd）内的课次明细")
    public String studentLessons(String studentName, String from, String to) {
        LocalDate f = LocalDate.parse(from);
        LocalDate t = LocalDate.parse(to);
        List<TimeSlot> slots = timeSlotService.listAll();
        List<String> lines = new ArrayList<>();
        YearMonth cur = YearMonth.from(f);
        YearMonth last = YearMonth.from(t);
        int guard = 0;
        while (!cur.isAfter(last) && guard++ < 24) {
            MonthGridVO grid = lessonService.getMonthGrid(cur.getYear(), cur.getMonthValue());
            if (grid.getCells() != null) {
                for (LessonCellVO c : grid.getCells().values()) {
                    if (!studentName.equals(c.getStudentName())) {
                        continue;
                    }
                    if (c.getLessonDate().isBefore(f) || c.getLessonDate().isAfter(t)) {
                        continue;
                    }
                    lines.add(c.getLessonDate() + " " + weekdayCN(c.getLessonDate()) + " "
                            + slotLabelById(slots, c.getSlotId()) + " " + statusLabel(c.getStatus())
                            + "（课次ID=" + c.getId() + "）");
                }
            }
            cur = cur.plusMonths(1);
        }
        if (lines.isEmpty()) {
            return studentName + " 在 " + from + " ~ " + to + " 之间没有排课。";
        }
        Collections.sort(lines);
        return studentName + " 在 " + from + " ~ " + to + " 的课次（" + lines.size() + " 节）：\n  "
                + String.join("\n  ", lines);
    }

    @Tool(description = "查询指定年月的排课统计：总课次 / 正常上课 / 顺延 / 已补 / 作废 / 待补")
    public String monthStatistic(int year, int month) {
        MonthStatisticVO s = statisticsService.monthStatistic(year, month);
        return year + "年" + month + "月统计：总课次 " + s.getScheduled()
                + "，正常上课 " + s.getNormal()
                + "，顺延 " + s.getAbsent() + "（学生请假 " + s.getAbsentByStudent()
                + " / 老师请假 " + s.getAbsentByTeacher() + "）"
                + "，已补 " + s.getMadeUp()
                + "，作废 " + s.getCancelled()
                + "，待补 " + s.getPending() + "。";
    }

    @Tool(description = "查询指定年月的预计收入（元）")
    public String monthIncome(int year, int month) {
        MonthIncomeVO vo = statisticsService.monthIncome(year, month);
        return year + "年" + month + "月预计收入：" + vo.getIncome() + " 元（口径：" + vo.getFormula() + "）";
    }

    @Tool(description = "查询当前所有待补课（顺延后尚未补完的课次）")
    public String pendingMakeUps() {
        List<Lesson> pending = lessonService.listPending();
        if (pending.isEmpty()) {
            return "当前没有待补课。";
        }
        Map<Long, String> names = studentNameMap();
        StringBuilder sb = new StringBuilder("当前待补课 " + pending.size() + " 节：");
        for (Lesson l : pending) {
            sb.append("\n  ").append(l.getLessonDate()).append(' ').append(weekdayCN(l.getLessonDate()))
                    .append(" 学生=").append(names.getOrDefault(l.getStudentId(), "id" + l.getStudentId()))
                    .append(" 原因=").append(l.getAbsentReason() == null ? "-" : l.getAbsentReason())
                    .append(" 课次ID=").append(l.getId());
        }
        return sb.toString();
    }

    @Tool(description = "排课体检：汇总漏排学生、各学生本月课时分布、待补积压，用于给老师提改进建议")
    public String scheduleHealth(int year, int month) {
        StringBuilder sb = new StringBuilder();
        sb.append("【漏排】").append(unscheduledStudents(year, month)).append('\n');

        MonthGridVO grid = lessonService.getMonthGrid(year, month);
        Map<String, Integer> byStudent = new HashMap<>();
        int total = 0;
        if (grid.getCells() != null) {
            for (LessonCellVO c : grid.getCells().values()) {
                byStudent.merge(c.getStudentName() == null ? "未指派" : c.getStudentName(), 1, Integer::sum);
                total++;
            }
        }
        sb.append("【课时分布】本月共 ").append(total).append(" 节。");
        if (byStudent.isEmpty()) {
            sb.append("暂无排课。");
        } else {
            List<String> parts = new ArrayList<>();
            byStudent.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> parts.add(e.getKey() + " " + e.getValue() + " 节"));
            sb.append(String.join("，", parts));
        }

        List<Lesson> pending = lessonService.listPending();
        sb.append('\n').append("【待补积压】").append(pending.size()).append(" 节");
        if (!pending.isEmpty()) {
            pending.sort((a, b) -> a.getLessonDate().compareTo(b.getLessonDate()));
            sb.append("，最早的是 ").append(pending.get(0).getLessonDate())
                    .append("（课次ID=").append(pending.get(0).getId()).append("）");
        }
        return sb.toString();
    }

    private List<Student> allStudents() {
        return studentService.listStudents(null, null, null, 1, 500).getList();
    }

    private Map<Long, String> studentNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (Student s : allStudents()) {
            map.put(s.getId(), s.getName());
        }
        return map;
    }

    private String slotLabelById(List<TimeSlot> slots, Long slotId) {
        if (slotId == null) {
            return "未知时段";
        }
        return slots.stream()
                .filter(t -> slotId.equals(t.getId()))
                .findFirst()
                .map(this::slotLabel)
                .orElse("时段" + slotId);
    }

    private String slotLabel(TimeSlot slot) {
        String start = slot.getStartTime() == null ? "" : HH_MM.format(slot.getStartTime());
        String end = slot.getEndTime() == null ? "" : HH_MM.format(slot.getEndTime());
        return start + "-" + end;
    }

    private String weekdayCN(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "UNTAKEN" -> "未上";
            case "NORMAL" -> "正常上课";
            case "ABSENT" -> "顺延";
            case "MADEUP" -> "已补";
            case "CANCELLED" -> "作废";
            default -> Objects.toString(status, "");
        };
    }
}
