package com.lesson.schedule.service;

import java.io.OutputStream;

/**
 * 排课表导出服务（Excel）。
 * <p>由后端生成 xlsx 二进制流，前端只负责触发下载（导出统一走后端接口）。</p>
 */
public interface LessonExportService {

    /**
     * 导出指定月份的排课表：Sheet「排课总表」（日期 × 时段 矩阵）+ Sheet「排课明细」（一行一条）。
     *
     * @param year  年
     * @param month 月
     * @param out   输出流（由 Controller 写入 HttpServletResponse）
     */
    void exportMonthExcel(int year, int month, OutputStream out);
}
