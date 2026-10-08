package com.lesson.schedule.common.vo;

import java.util.List;
import lombok.Data;

/** 确认执行结果：各类动作成功数与失败原因。 */
@Data
public class AiApplyResultVO {

    private int created;
    private int deleted;
    private int updated;
    private int failed;

    /** 逐条结果说明（失败原因 / 跳过原因） */
    private List<String> messages;
}
