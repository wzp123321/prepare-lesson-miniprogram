package com.lesson.schedule.common.vo;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 试卷详情：卷信息 + 按题号顺序的题目列表。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaperDetailVO extends PaperVO {

    private List<QuestionVO> questions;
}
