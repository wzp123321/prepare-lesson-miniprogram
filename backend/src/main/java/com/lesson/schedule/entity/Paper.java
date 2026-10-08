package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 试卷。对应表 {@code paper}。
 * 通用卷 studentId 为空；派生给学生后 studentId = 该生，parentPaperId 记录来源。
 */
@Data
@TableName("paper")
public class Paper {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卷名，如「五年级 修辞手法 专项」 */
    private String title;

    private String grade;

    /** KP 知识点专项卷 / LESSON 课时题单 */
    private String paperType;

    /** 归属学生，NULL = 通用卷 */
    private Long studentId;

    /** 派生自哪份卷（clone 溯源） */
    private Long parentPaperId;

    /** DRAFT 草稿 / READY 可用 */
    private String status;

    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
