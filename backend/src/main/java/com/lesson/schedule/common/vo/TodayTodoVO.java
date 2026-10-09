package com.lesson.schedule.common.vo;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/**
 * 今日待办聚合视图（BE-API-34）。
 * <p>回答老师开机第一个问题「现在该做什么」，把原先散在 4 个页面的待办合成一处：
 * <ol>
 *   <li>{@code markToday}  今天有课但还没标记上没上（含顺延需登记）</li>
 *   <li>{@code makeUpToday} 今天该补的课（make_up_date = 今天但仍是 ABSENT）</li>
 *   <li>{@code prepToday}  明天要上但还没配知识点的课（提前一晚备课）</li>
 *   <li>{@code makeUpOverdue} 已过期未处理的待补课（原顺延日期早于今天且未关闭）</li>
 * </ol>
 * 只读聚合，不改任何状态；处理器仍走各模块既有接口。
 */
@Data
public class TodayTodoVO {

    /** 待办日期（= 今天） */
    private LocalDate today;

    /** 今天有课但未标记的课次 */
    private List<TodoItemVO> markToday;

    /** 今天该补的课次 */
    private List<TodoItemVO> makeUpToday;

    /** 明天要上但还没备课的课次 */
    private List<TodoItemVO> prepToday;

    /** 逾期待补（原顺延日期早于今天且未关闭） */
    private List<TodoItemVO> makeUpOverdue;

    /** 合计条数（前端空态判断用，避免四个数组各自判空） */
    private int total;
}
