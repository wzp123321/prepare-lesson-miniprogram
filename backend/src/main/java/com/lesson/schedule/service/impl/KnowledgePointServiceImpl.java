package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.dto.KpQueryDTO;
import com.lesson.schedule.common.vo.KpVO;
import com.lesson.schedule.entity.KnowledgePoint;
import com.lesson.schedule.entity.LessonKnowledge;
import com.lesson.schedule.entity.Question;
import com.lesson.schedule.mapper.KnowledgePointMapper;
import com.lesson.schedule.mapper.LessonKnowledgeMapper;
import com.lesson.schedule.mapper.QuestionMapper;
import com.lesson.schedule.service.KnowledgePointService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgePointServiceImpl implements KnowledgePointService {

    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionMapper questionMapper;
    private final LessonKnowledgeMapper lessonKnowledgeMapper;

    @Override
    public List<KpVO> list(KpQueryDTO query) {
        var qw = Wrappers.<KnowledgePoint>lambdaQuery();
        if (query != null) {
            if (query.getGrade() != null && !query.getGrade().isBlank()) {
                qw.eq(KnowledgePoint::getGrade, query.getGrade());
            }
            if (query.getCategory() != null && !query.getCategory().isBlank()) {
                qw.eq(KnowledgePoint::getCategory, query.getCategory());
            }
            if (query.getEnabled() != null) {
                qw.eq(KnowledgePoint::getEnabled, query.getEnabled());
            }
        }
        qw.orderByAsc(KnowledgePoint::getGrade)
                .orderByAsc(KnowledgePoint::getSortOrder)
                .orderByAsc(KnowledgePoint::getId);
        List<KnowledgePoint> rows = knowledgePointMapper.selectList(qw);

        Map<Long, Long> counts = countCommonQuestionsByKp();
        List<KpVO> result = new ArrayList<>(rows.size());
        for (KnowledgePoint kp : rows) {
            KpVO vo = new KpVO();
            vo.setId(kp.getId());
            vo.setName(kp.getName());
            vo.setGrade(kp.getGrade());
            vo.setCategory(kp.getCategory());
            vo.setParentId(kp.getParentId());
            vo.setSortOrder(kp.getSortOrder());
            vo.setEnabled(kp.getEnabled());
            vo.setQuestionCount(counts.getOrDefault(kp.getId(), 0L));
            result.add(vo);
        }
        return result;
    }

    /** 通用题库（student_id 为空）按知识点聚合题量。 */
    private Map<Long, Long> countCommonQuestionsByKp() {
        Map<Long, Long> counts = new HashMap<>();
        List<Question> rows = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .select(Question::getKpId)
                .isNull(Question::getStudentId));
        for (Question q : rows) {
            if (q.getKpId() != null) {
                counts.merge(q.getKpId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    @Override
    public Long create(KnowledgePoint kp) {
        if (kp.getName() == null || kp.getName().isBlank()) {
            throw new BusinessException(400, "知识点名称不能为空");
        }
        var dupQw = Wrappers.<KnowledgePoint>lambdaQuery().eq(KnowledgePoint::getName, kp.getName());
        if (kp.getGrade() == null || kp.getGrade().isBlank()) {
            dupQw.isNull(KnowledgePoint::getGrade);
        } else {
            dupQw.eq(KnowledgePoint::getGrade, kp.getGrade());
        }
        Long dup = knowledgePointMapper.selectCount(dupQw);
        if (dup != null && dup > 0) {
            throw new BusinessException(409, "该年级下已存在同名知识点");
        }
        if (kp.getEnabled() == null) {
            kp.setEnabled(1);
        }
        if (kp.getSortOrder() == null) {
            kp.setSortOrder(0);
        }
        kp.setId(null);
        knowledgePointMapper.insert(kp);
        return kp.getId();
    }

    @Override
    public void update(Long id, KnowledgePoint kp) {
        KnowledgePoint existing = knowledgePointMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "知识点不存在");
        }
        if (kp.getName() != null && !kp.getName().isBlank()) {
            existing.setName(kp.getName());
        }
        if (kp.getGrade() != null) {
            existing.setGrade(kp.getGrade());
        }
        if (kp.getCategory() != null) {
            existing.setCategory(kp.getCategory());
        }
        if (kp.getParentId() != null) {
            existing.setParentId(kp.getParentId());
        }
        if (kp.getSortOrder() != null) {
            existing.setSortOrder(kp.getSortOrder());
        }
        if (kp.getEnabled() != null) {
            existing.setEnabled(kp.getEnabled());
        }
        knowledgePointMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        KnowledgePoint existing = knowledgePointMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "知识点不存在");
        }
        long usedByQuestion = questionMapper.selectCount(
                Wrappers.<Question>lambdaQuery().eq(Question::getKpId, id));
        if (usedByQuestion > 0) {
            throw new BusinessException(409, "该知识点已被 " + usedByQuestion + " 道题引用，建议改为停用");
        }
        long usedByLesson = lessonKnowledgeMapper.selectCount(
                Wrappers.<LessonKnowledge>lambdaQuery().eq(LessonKnowledge::getKpId, id));
        if (usedByLesson > 0) {
            throw new BusinessException(409, "该知识点已被 " + usedByLesson + " 节课引用，建议改为停用");
        }
        knowledgePointMapper.deleteById(id);
    }
}
