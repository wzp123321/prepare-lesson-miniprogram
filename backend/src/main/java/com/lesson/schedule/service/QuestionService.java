package com.lesson.schedule.service;

import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.dto.QuestionQueryDTO;
import com.lesson.schedule.common.vo.QuestionDupCheckVO;
import com.lesson.schedule.common.vo.QuestionVO;
import com.lesson.schedule.entity.Question;
import java.util.List;

/** 题库服务。 */
public interface QuestionService {

    /** 分页列表（grade / kpId / qtype / keyword / studentId 过滤）。 */
    PageResult<QuestionVO> list(QuestionQueryDTO query);

    /** 详情。 */
    QuestionVO get(Long id);

    /** 录题。 */
    Long create(Question question);

    /** 批量入库（粘贴整卷导入）；跳过空题干，返回成功条数。 */
    int batchCreate(List<Question> questions);

    /**
     * 批量题干查重（导入核对用）。
     * 顺序与入参一一对应，未命中时对应项字段为空。
     */
    List<QuestionDupCheckVO> checkDuplicates(List<String> stems);

    /** 改题（同步影响引用它的通用卷）。 */
    void update(Long id, Question question);

    /** 删题；被试卷引用时返回 409。 */
    void delete(Long id);
}
