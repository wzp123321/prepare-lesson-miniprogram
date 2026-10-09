package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/**
 * 组卷落库入参：老师确认后的题目顺序即为题号顺序。
 * 与 {@link PaperQuestionsSetDTO} 的区别是这里同时带卷头信息，一步建卷 + 编排。
 */
@Data
public class PaperGenerateCommitDTO {

    private String title;
    private String grade;
    private String paperType;
    private Long studentId;
    private String remark;
    /** 确认后的题目，顺序即题号顺序 */
    private List<Long> questionIds;
}
