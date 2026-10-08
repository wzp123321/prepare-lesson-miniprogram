package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/** 试卷题目编排（整体覆盖）。questionIds 的顺序即题号顺序。 */
@Data
public class PaperQuestionsSetDTO {

    private Long paperId;
    private List<Long> questionIds;
}
