package com.lesson.schedule.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.excel.write.style.column.SimpleColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.vo.LessonCellVO;
import com.lesson.schedule.common.vo.MonthGridVO;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.mapper.LessonMapper;
import com.lesson.schedule.service.LessonExportService;
import com.lesson.schedule.service.LessonService;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.springframework.stereotype.Service;

/**
 * 排课表导出实现（EasyExcel）。
 * <p>复用 {@link LessonService#getMonthGrid} 的网格数据，避免重复查询逻辑；
 * 两张表：Sheet「排课总表」（日期 × 时段 矩阵）+ Sheet「排课明细」（一行一条）。</p>
 */
@Service
@RequiredArgsConstructor
public class LessonExportServiceImpl implements LessonExportService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final LessonService lessonService;
    private final LessonMapper lessonMapper;

    @Override
    public void exportMonthExcel(int year, int month, OutputStream out) {
        MonthGridVO grid = lessonService.getMonthGrid(year, month);
        List<String> days = grid.getDays();
        List<TimeSlot> slots = grid.getSlots();
        Map<String, LessonCellVO> cells = grid.getCells();
        // remark 不在 LessonCellVO 中，单独取一次
        Map<Long, String> remarks = loadRemarks(days);

        ExcelWriter writer = EasyExcel.write(out)
                .registerWriteHandler(buildStyleStrategy())
                .registerWriteHandler(new SimpleColumnWidthStyleStrategy(18))
                .build();
        try {
            writer.write(buildSummaryData(days, slots, cells),
                    EasyExcel.writerSheet(0, "排课总表").head(buildSummaryHead(slots)).build());
            writer.write(buildDetailData(days, slots, cells, remarks),
                    EasyExcel.writerSheet(1, "排课明细").head(buildDetailHead()).build());
        } catch (Exception e) {
            throw new BusinessException(500, "导出 Excel 失败：" + e.getMessage());
        } finally {
            writer.finish();
        }
    }

    /** Sheet1 表头：日期 + 星期 + 各时段 */
    private List<List<String>> buildSummaryHead(List<TimeSlot> slots) {
        List<List<String>> head = new ArrayList<>();
        head.add(Collections.singletonList("日期"));
        head.add(Collections.singletonList("星期"));
        for (TimeSlot slot : slots) {
            head.add(Collections.singletonList(slotLabel(slot)));
        }
        return head;
    }

    /** Sheet1 数据：一行一天 */
    private List<List<Object>> buildSummaryData(List<String> days, List<TimeSlot> slots,
                                                Map<String, LessonCellVO> cells) {
        List<List<Object>> data = new ArrayList<>();
        for (String day : days) {
            List<Object> row = new ArrayList<>();
            row.add(day.length() > 5 ? day.substring(5) : day);
            row.add(weekdayCN(day));
            for (TimeSlot slot : slots) {
                row.add(cellText(cells.get(cellKey(day, slot.getId()))));
            }
            data.add(row);
        }
        return data;
    }

    private List<List<String>> buildDetailHead() {
        String[] heads = {"日期", "星期", "时段", "学生", "状态", "请假方", "顺延原因", "补课日期", "备注"};
        List<List<String>> head = new ArrayList<>(heads.length);
        for (String h : heads) {
            head.add(Collections.singletonList(h));
        }
        return head;
    }

    /** Sheet2 数据：一行一条排课 */
    private List<List<Object>> buildDetailData(List<String> days, List<TimeSlot> slots,
                                               Map<String, LessonCellVO> cells, Map<Long, String> remarks) {
        List<List<Object>> data = new ArrayList<>();
        for (String day : days) {
            for (TimeSlot slot : slots) {
                LessonCellVO cell = cells.get(cellKey(day, slot.getId()));
                if (cell == null) {
                    continue;
                }
                List<Object> row = new ArrayList<>();
                row.add(day);
                row.add(weekdayCN(day));
                row.add(slotLabel(slot));
                row.add(nullToEmpty(cell.getStudentName()));
                row.add(statusLabel(cell.getStatus()));
                row.add(absentByLabel(cell.getAbsentBy()));
                row.add(nullToEmpty(cell.getAbsentReason()));
                row.add(cell.getMakeUpDate() == null ? "" : cell.getMakeUpDate().toString());
                row.add(remarks.getOrDefault(cell.getId(), ""));
                data.add(row);
            }
        }
        if (data.isEmpty()) {
            data.add(Collections.singletonList("本月暂无排课"));
        }
        return data;
    }

    /** 表头灰底加粗 + 内容居中带边框；列宽取两表较宽者（明细表 9 列） */
    private HorizontalCellStyleStrategy buildStyleStrategy() {
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        WriteFont headFont = new WriteFont();
        headFont.setBold(true);
        headStyle.setWriteFont(headFont);
        setBorder(headStyle);

        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        contentStyle.setWrapped(false);
        setBorder(contentStyle);

        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }

    private void setBorder(WriteCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /** 该月课次备注（lessonId → remark），仅取非空。 */
    private Map<Long, String> loadRemarks(List<String> days) {
        Map<Long, String> map = new HashMap<>();
        if (days == null || days.isEmpty()) {
            return map;
        }
        LocalDate from = LocalDate.parse(days.get(0));
        LocalDate to = LocalDate.parse(days.get(days.size() - 1));
        List<Lesson> list = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .ge(Lesson::getLessonDate, from)
                .le(Lesson::getLessonDate, to));
        for (Lesson l : list) {
            if (l.getRemark() != null && !l.getRemark().isBlank()) {
                map.put(l.getId(), l.getRemark());
            }
        }
        return map;
    }

    private String cellText(LessonCellVO cell) {
        if (cell == null) {
            return "";
        }
        String name = nullToEmpty(cell.getStudentName());
        if ("UNTAKEN".equals(cell.getStatus())) {
            return name;
        }
        return name + "（" + statusLabel(cell.getStatus()) + "）";
    }

    private String cellKey(String day, Long slotId) {
        return day + "#" + slotId;
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
            default -> status;
        };
    }

    private String absentByLabel(String absentBy) {
        if (absentBy == null) {
            return "";
        }
        return switch (absentBy) {
            case "student" -> "学生";
            case "teacher" -> "老师";
            default -> absentBy;
        };
    }

    private String weekdayCN(String day) {
        return switch (LocalDate.parse(day).getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private String slotLabel(TimeSlot slot) {
        String start = slot.getStartTime() == null ? "" : HH_MM.format(slot.getStartTime());
        String end = slot.getEndTime() == null ? "" : HH_MM.format(slot.getEndTime());
        return start + "-" + end;
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
