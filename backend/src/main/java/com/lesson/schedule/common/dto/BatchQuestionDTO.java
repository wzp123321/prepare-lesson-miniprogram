package com.lesson.schedule.common.dto;

import com.lesson.schedule.entity.Question;
import java.util.List;
import lombok.Data;

/** 批量入库题目（题库「粘贴整卷 → 批量导入」用）。 */
@Data
public class BatchQuestionDTO {

    private List<Question> questions;
}
