package com.lesson.schedule.common.vo;

import java.util.List;
import lombok.Data;

/**
 * 智能排课的「反问」：写操作缺必填参数时，AI 不瞎猜，而是给出候选项让老师点选。
 * <p>前端渲染成可点选项；老师点选后带 selection 再发一轮，直到参数凑齐才输出 actions。</p>
 * <p>与 {@code AiChatVO#actions} 互斥：要么出方案，要么先把参数问清楚。</p>
 */
@Data
public class AiAskVO {

    /** 缺哪个参数：studentId / lessonDate / slotId / lessonId / absentBy / makeUpDate 等 */
    private String field;

    /** 给老师看的问题，如「这一节排给哪位学生？」 */
    private String label;

    /** 候选项（必须取自后端给定的学生 / 时段 / 已有课次，禁止编造） */
    private List<Option> options;

    /** 单个候选项 */
    @Data
    public static class Option {

        /** 机器值：与 field 对应的 id 或字面量 */
        private String value;

        /** 显示文本，如「张三 · 五年级」 */
        private String label;

        /** 可选补充说明，如「本月已排 3 节」 */
        private String hint;
    }
}
