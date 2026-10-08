package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 通用主键请求体。动作化接口用 body 传 id，避免路径变量（契合「路径不出现参数」）。
 * 用于 detail / update / delete / pause / disable / made-up 等只需主键的接口。
 */
@Data
public class IdDTO {

    /** 业务主键 */
    private Long id;
}
