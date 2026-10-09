package com.lesson.schedule.common.dto;

import lombok.Data;

/** 单个知识点的抽题量：kpId 抽 count 题。 */
@Data
public class KpCountDTO {

    private Long kpId;
    /** 该知识点抽几题；<=0 视为不抽 */
    private Integer count;
}
