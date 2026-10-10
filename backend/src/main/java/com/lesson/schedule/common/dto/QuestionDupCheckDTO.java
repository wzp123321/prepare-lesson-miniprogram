package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/**
 * 批量导入前的题干查重（题库「粘贴整卷 → 核对」用）。
 * <p>一次提交本批全部题干，后端批量比对，避免前端逐条请求。</p>
 */
@Data
public class QuestionDupCheckDTO {

    /** 待检查的题干列表，顺序与返回结果一一对应 */
    private List<String> stems;
}
