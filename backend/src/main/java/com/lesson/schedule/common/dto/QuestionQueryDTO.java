package com.lesson.schedule.common.dto;

import lombok.Data;

/** 题库列表查询（分页）。入参：grade / kpId / qtype / keyword / studentId / page / size。 */
@Data
public class QuestionQueryDTO {

    private String grade;
    private Long kpId;
    private String qtype;
    /** 关键词，仅匹配题干 */
    private String keyword;
    /** 归属学生；配 onlyCommon=true 时忽略 */
    private Long studentId;
    /** true = 只看通用题（studentId 为空的题），用于通用组卷 */
    private Boolean onlyCommon;
    private Long page = 1L;
    private Long size = 20L;
}
