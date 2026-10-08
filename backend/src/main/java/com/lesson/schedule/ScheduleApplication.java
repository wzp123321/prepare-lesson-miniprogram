package com.lesson.schedule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 备课排课管理系统 启动类。
 *
 * <p>@MapperScan 扫描 mapper 包，使 MyBatis-Plus 的 BaseMapper 实现被注入容器。</p>
 */
@SpringBootApplication
@MapperScan("com.lesson.schedule.mapper")
public class ScheduleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleApplication.class, args);
    }
}
