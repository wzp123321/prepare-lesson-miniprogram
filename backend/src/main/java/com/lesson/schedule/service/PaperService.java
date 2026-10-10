package com.lesson.schedule.service;

import com.lesson.schedule.common.dto.PaperGenerateCommitDTO;
import com.lesson.schedule.common.dto.PaperGenerateDTO;
import com.lesson.schedule.common.dto.PaperQueryDTO;
import com.lesson.schedule.common.dto.PaperQuestionsSetDTO;
import com.lesson.schedule.common.dto.PaperReuseDTO;
import com.lesson.schedule.common.vo.PaperDetailVO;
import com.lesson.schedule.common.vo.PaperGeneratePreviewVO;
import com.lesson.schedule.common.vo.PaperVO;
import com.lesson.schedule.entity.Paper;
import java.util.List;

/** 试卷服务。 */
public interface PaperService {

    /** 列表（grade / studentId / paperType / keyword 过滤），带题数。 */
    List<PaperVO> list(PaperQueryDTO query);

    /** 详情：卷信息 + 按题号顺序的题目。 */
    PaperDetailVO get(Long id);

    /** 建卷。 */
    Long create(Paper paper);

    /** 改卷（部分字段）。 */
    void update(Long id, Paper paper);

    /** 删卷（同时删编排与课次关联，题目保留在题库）。 */
    void delete(Long id);

    /** 派生：克隆卷 + 克隆题目，改后不影响原卷。 */
    Long clone(Long id, Long studentId);

    /** 一键复用：以蓝本卷生成一份新的可编辑卷（复制编排 + 卷内快照，不克隆题库行）。 */
    Long reuse(PaperReuseDTO dto);

    /** 编排题目（整体覆盖，数组顺序即题号顺序；可携带卷内编辑快照）。 */
    void setQuestions(PaperQuestionsSetDTO dto);

    /** 一键组卷：按知识点随机抽题成卷（直接落库，保留兼容）。 */
    Long generate(PaperGenerateDTO dto);

    /** 组卷试抽：只按条件抽题并返回，不落库；支持按知识点分别设量。 */
    PaperGeneratePreviewVO generatePreview(PaperGenerateDTO dto);

    /** 组卷落库：按试抽确认后的题目顺序建卷并编排。 */
    Long generateCommit(PaperGenerateCommitDTO dto);
}
