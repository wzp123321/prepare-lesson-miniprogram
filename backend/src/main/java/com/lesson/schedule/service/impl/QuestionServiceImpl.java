package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.dto.QuestionQueryDTO;
import com.lesson.schedule.common.vo.QuestionVO;
import com.lesson.schedule.entity.KnowledgePoint;
import com.lesson.schedule.entity.PaperQuestion;
import com.lesson.schedule.entity.Question;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.mapper.KnowledgePointMapper;
import com.lesson.schedule.mapper.PaperQuestionMapper;
import com.lesson.schedule.mapper.QuestionMapper;
import com.lesson.schedule.mapper.StudentMapper;
import com.lesson.schedule.service.QuestionService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final StudentMapper studentMapper;
    private final PaperQuestionMapper paperQuestionMapper;

    @Override
    public PageResult<QuestionVO> list(QuestionQueryDTO query) {
        long page = query == null || query.getPage() == null ? 1L : query.getPage();
        long size = query == null || query.getSize() == null ? 20L : query.getSize();
        var qw = Wrappers.<Question>lambdaQuery();
        if (query != null) {
            if (query.getGrade() != null && !query.getGrade().isBlank()) {
                qw.eq(Question::getGrade, query.getGrade());
            }
            if (query.getKpId() != null) {
                qw.eq(Question::getKpId, query.getKpId());
            }
            if (query.getQtype() != null && !query.getQtype().isBlank()) {
                qw.eq(Question::getQtype, query.getQtype());
            }
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                qw.like(Question::getStem, query.getKeyword());
            }
            if (Boolean.TRUE.equals(query.getOnlyCommon())) {
                qw.isNull(Question::getStudentId);
            } else if (query.getStudentId() != null) {
                qw.eq(Question::getStudentId, query.getStudentId());
            }
        }
        qw.orderByDesc(Question::getId);
        Page<Question> p = questionMapper.selectPage(new Page<>(page, size), qw);
        return new PageResult<>(toVOs(p.getRecords()), p.getTotal(), page, size);
    }

    @Override
    public QuestionVO get(Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw new BusinessException(404, "题目不存在");
        }
        List<QuestionVO> vos = toVOs(List.of(q));
        return vos.get(0);
    }

    @Override
    public Long create(Question question) {
        if (question.getStem() == null || question.getStem().isBlank()) {
            throw new BusinessException(400, "题干不能为空");
        }
        if (question.getKpId() != null && knowledgePointMapper.selectById(question.getKpId()) == null) {
            throw new BusinessException(400, "所选知识点不存在");
        }
        if (question.getDifficulty() == null) {
            question.setDifficulty(2);
        }
        question.setId(null);
        questionMapper.insert(question);
        return question.getId();
    }

    @Override
    public int batchCreate(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(400, "没有可入库的题目");
        }
        if (questions.size() > 200) {
            throw new BusinessException(400, "一次最多入库 200 道题");
        }
        int ok = 0;
        for (Question q : questions) {
            if (q == null || q.getStem() == null || q.getStem().isBlank()) {
                continue; // 跳过无效项，不中断整批
            }
            if (q.getKpId() != null && knowledgePointMapper.selectById(q.getKpId()) == null) {
                q.setKpId(null); // 知识点已不存在时置空，不中断整批
            }
            if (q.getDifficulty() == null) {
                q.setDifficulty(2);
            }
            q.setId(null);
            questionMapper.insert(q);
            ok++;
        }
        if (ok == 0) {
            throw new BusinessException(400, "没有有效题目（题干为空）");
        }
        return ok;
    }

    @Override
    public void update(Long id, Question question) {
        Question existing = questionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "题目不存在");
        }
        if (question.getStem() != null && !question.getStem().isBlank()) {
            existing.setStem(question.getStem());
        }
        if (question.getGrade() != null) {
            existing.setGrade(question.getGrade());
        }
        if (question.getKpId() != null) {
            existing.setKpId(question.getKpId());
        }
        if (question.getStudentId() != null) {
            existing.setStudentId(question.getStudentId());
        }
        if (question.getQtype() != null) {
            existing.setQtype(question.getQtype());
        }
        if (question.getOptions() != null) {
            existing.setOptions(question.getOptions());
        }
        if (question.getAnswer() != null) {
            existing.setAnswer(question.getAnswer());
        }
        if (question.getAnalysis() != null) {
            existing.setAnalysis(question.getAnalysis());
        }
        if (question.getDifficulty() != null) {
            existing.setDifficulty(question.getDifficulty());
        }
        if (question.getSource() != null) {
            existing.setSource(question.getSource());
        }
        questionMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        Question existing = questionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "题目不存在");
        }
        long used = paperQuestionMapper.selectCount(
                Wrappers.<PaperQuestion>lambdaQuery().eq(PaperQuestion::getQuestionId, id));
        if (used > 0) {
            throw new BusinessException(409, "该题已被 " + used + " 份试卷使用，请先从卷中移除");
        }
        questionMapper.deleteById(id);
    }

    /** 批量补知识点名与学生名，避免逐行查库。 */
    private List<QuestionVO> toVOs(List<Question> rows) {
        List<QuestionVO> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Set<Long> kpIds = new HashSet<>();
        Set<Long> stuIds = new HashSet<>();
        for (Question q : rows) {
            if (q.getKpId() != null) {
                kpIds.add(q.getKpId());
            }
            if (q.getStudentId() != null) {
                stuIds.add(q.getStudentId());
            }
        }
        Map<Long, String> kpNames = new HashMap<>();
        if (!kpIds.isEmpty()) {
            for (KnowledgePoint kp : knowledgePointMapper.selectBatchIds(kpIds)) {
                kpNames.put(kp.getId(), kp.getName());
            }
        }
        Map<Long, String> stuNames = new HashMap<>();
        if (!stuIds.isEmpty()) {
            for (Student s : studentMapper.selectBatchIds(stuIds)) {
                stuNames.put(s.getId(), s.getName());
            }
        }
        for (Question q : rows) {
            QuestionVO vo = new QuestionVO();
            vo.setId(q.getId());
            vo.setGrade(q.getGrade());
            vo.setKpId(q.getKpId());
            vo.setKpName(q.getKpId() == null ? null : kpNames.get(q.getKpId()));
            vo.setStudentId(q.getStudentId());
            vo.setStudentName(q.getStudentId() == null ? null : stuNames.get(q.getStudentId()));
            vo.setQtype(q.getQtype());
            vo.setStem(q.getStem());
            vo.setOptions(q.getOptions());
            vo.setAnswer(q.getAnswer());
            vo.setAnalysis(q.getAnalysis());
            vo.setDifficulty(q.getDifficulty());
            vo.setSource(q.getSource());
            vo.setCreateTime(q.getCreateTime());
            vo.setUpdateTime(q.getUpdateTime());
            result.add(vo);
        }
        return result;
    }
}
