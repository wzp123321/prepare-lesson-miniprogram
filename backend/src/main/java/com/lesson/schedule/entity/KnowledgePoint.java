package com.lesson.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 语文知识点（备课模块）。对应表 {@code knowledge_point}。
 * 备课以「年级 + 知识点」组织，试卷、题目、课次均挂靠知识点。
 */
@Data
@TableName("knowledge_point")
public class KnowledgePoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识点名称，如「修辞手法」 */
    private String name;

    /** 适用年级（dict.grade），空 = 通用 */
    private String grade;

    /** 大类：字词 / 句子 / 阅读 / 古诗文 / 写作 / 基础 */
    private String category;

    /** 父知识点（可选，支持二级） */
    private Long parentId;

    private Integer sortOrder;

    /** 启用 1/0 */
    private Integer enabled;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
