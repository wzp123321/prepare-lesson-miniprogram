package com.lesson.schedule.common.dto;

import lombok.Data;

/**
 * 老师对上一轮 {@code AiAskVO} 的点选结果。
 * <p>结构化回传（而不是只把显示文本拼进消息），避免模型把「张三 · 五年级」错认成姓名或 id。</p>
 */
@Data
public class AiSelectionDTO {

    /** 对应上一轮 ask 的 field */
    private String field;

    /** 选中的机器值（学生 id / 日期 / 时段 id 等） */
    private String value;

    /** 选中的显示文本，仅用于回显 */
    private String label;
}
