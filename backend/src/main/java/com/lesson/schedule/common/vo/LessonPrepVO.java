package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 单节课的备课详情（抽屉回显用）。 */
@Data
public class LessonPrepVO {

    private Long lessonId;
    private Long studentId;
    private String studentName;
    private String grade;
    private LocalDate lessonDate;
    private List<Long> kpIds;
    private List<Long> paperIds;
    private String remark;
}
