package com.lesson.schedule.common.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 试卷（列表用，带题数与归属学生名）。 */
@Data
public class PaperVO {

    private Long id;
    private String title;
    private String grade;
    private String paperType;
    private Long studentId;
    private String studentName;
    private Long parentPaperId;
    private String parentTitle;
    private String status;
    private String remark;

    /** 卷内题目数 */
    private Long questionCount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
