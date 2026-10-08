package com.lesson.schedule.common;

import java.util.List;
import lombok.Data;

/**
 * 分页结果（统一分页契约 {@code {list, total, page, size}}）。
 *
 * @param <T> 数据行类型
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> list;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 起） */
    private long page;

    /** 每页大小 */
    private long size;

    public PageResult(List<T> list, long total, long page, long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
