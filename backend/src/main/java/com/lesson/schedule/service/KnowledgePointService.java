package com.lesson.schedule.service;

import com.lesson.schedule.common.dto.KpQueryDTO;
import com.lesson.schedule.common.vo.KpVO;
import com.lesson.schedule.entity.KnowledgePoint;
import java.util.List;

/** 知识点服务（备课模块）。 */
public interface KnowledgePointService {

    /** 列表（按年级 / 大类 / 启用过滤），带该知识点通用题量。 */
    List<KpVO> list(KpQueryDTO query);

    /** 新增，同 (grade, name) 重复返回 409。 */
    Long create(KnowledgePoint kp);

    /** 修改（部分字段）。 */
    void update(Long id, KnowledgePoint kp);

    /** 删除；被题目或课次引用时返回 409（建议改为停用）。 */
    void delete(Long id);
}
