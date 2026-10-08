package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 待备课列表行：课次 + 已配知识点 / 试卷 + 备课状态。 */
@Data
public class TodoPrepVO {

    private Long lessonId;
    private LocalDate lessonDate;
    private Long slotId;
    private String slotStart;
    private String slotEnd;
    private Long studentId;
    private String studentName;
    private String grade;
    /** 课次状态 UNTAKEN / NORMAL / ABSENT / MADEUP / CANCELLED */
    private String lessonStatus;

    private List<Long> kpIds;
    private List<String> kpNames;
    private List<Long> paperIds;
    private List<String> paperTitles;

    /** 是否已备课（至少配了 1 个知识点） */
    private Boolean prepared;
    private String remark;
}
