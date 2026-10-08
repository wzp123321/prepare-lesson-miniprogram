package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.dto.PaperGenerateDTO;
import com.lesson.schedule.common.dto.PaperQueryDTO;
import com.lesson.schedule.common.dto.PaperQuestionsSetDTO;
import com.lesson.schedule.common.vo.PaperDetailVO;
import com.lesson.schedule.common.vo.PaperVO;
import com.lesson.schedule.common.vo.QuestionVO;
import com.lesson.schedule.entity.KnowledgePoint;
import com.lesson.schedule.entity.LessonPaper;
import com.lesson.schedule.entity.Paper;
import com.lesson.schedule.entity.PaperQuestion;
import com.lesson.schedule.entity.Question;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.mapper.KnowledgePointMapper;
import com.lesson.schedule.mapper.LessonPaperMapper;
import com.lesson.schedule.mapper.PaperMapper;
import com.lesson.schedule.mapper.PaperQuestionMapper;
import com.lesson.schedule.mapper.QuestionMapper;
import com.lesson.schedule.mapper.StudentMapper;
import com.lesson.schedule.service.PaperService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaperServiceImpl implements PaperService {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final StudentMapper studentMapper;
    private final LessonPaperMapper lessonPaperMapper;

    @Override
    public List<PaperVO> list(PaperQueryDTO query) {
        var qw = Wrappers.<Paper>lambdaQuery();
        if (query != null) {
            if (query.getGrade() != null && !query.getGrade().isBlank()) {
                qw.eq(Paper::getGrade, query.getGrade());
            }
            if (query.getStudentId() != null) {
                qw.eq(Paper::getStudentId, query.getStudentId());
            }
            if (query.getPaperType() != null && !query.getPaperType().isBlank()) {
                qw.eq(Paper::getPaperType, query.getPaperType());
            }
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                qw.like(Paper::getTitle, query.getKeyword());
            }
        }
        qw.orderByDesc(Paper::getId);
        List<Paper> rows = paperMapper.selectList(qw);
        List<PaperVO> result = new ArrayList<>();
        if (rows.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> stuIds = new HashSet<>();
        Set<Long> parentIds = new HashSet<>();
        for (Paper p : rows) {
            ids.add(p.getId());
            if (p.getStudentId() != null) {
                stuIds.add(p.getStudentId());
            }
            if (p.getParentPaperId() != null) {
                parentIds.add(p.getParentPaperId());
            }
        }
        Map<Long, Long> counts = countQuestionsByPaper(ids);
        Map<Long, String> stuNames = nameMap(stuIds, true);
        Map<Long, String> parentTitles = titleMap(parentIds);
        for (Paper p : rows) {
            PaperVO vo = new PaperVO();
            fillBasic(vo, p);
            vo.setQuestionCount(counts.getOrDefault(p.getId(), 0L));
            vo.setStudentName(p.getStudentId() == null ? null : stuNames.get(p.getStudentId()));
            vo.setParentTitle(p.getParentPaperId() == null ? null : parentTitles.get(p.getParentPaperId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public PaperDetailVO get(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException(404, "试卷不存在");
        }
        PaperDetailVO vo = new PaperDetailVO();
        fillBasic(vo, paper);
        if (paper.getStudentId() != null) {
            Student s = studentMapper.selectById(paper.getStudentId());
            vo.setStudentName(s == null ? null : s.getName());
        }
        if (paper.getParentPaperId() != null) {
            Paper parent = paperMapper.selectById(paper.getParentPaperId());
            vo.setParentTitle(parent == null ? null : parent.getTitle());
        }
        List<PaperQuestion> links = paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, id)
                .orderByAsc(PaperQuestion::getSortOrder)
                .orderByAsc(PaperQuestion::getId));
        List<Long> qIds = new ArrayList<>();
        for (PaperQuestion l : links) {
            qIds.add(l.getQuestionId());
        }
        vo.setQuestionCount((long) qIds.size());
        vo.setQuestions(toQuestionVOs(qIds));
        return vo;
    }

    @Override
    public Long create(Paper paper) {
        if (paper.getTitle() == null || paper.getTitle().isBlank()) {
            throw new BusinessException(400, "卷名不能为空");
        }
        if (paper.getStudentId() != null && studentMapper.selectById(paper.getStudentId()) == null) {
            throw new BusinessException(400, "所选学生不存在");
        }
        if (paper.getPaperType() == null || paper.getPaperType().isBlank()) {
            paper.setPaperType("KP");
        }
        if (paper.getStatus() == null || paper.getStatus().isBlank()) {
            paper.setStatus("DRAFT");
        }
        paper.setId(null);
        paperMapper.insert(paper);
        return paper.getId();
    }

    @Override
    public void update(Long id, Paper paper) {
        Paper existing = paperMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "试卷不存在");
        }
        if (paper.getTitle() != null && !paper.getTitle().isBlank()) {
            existing.setTitle(paper.getTitle());
        }
        if (paper.getGrade() != null) {
            existing.setGrade(paper.getGrade());
        }
        if (paper.getPaperType() != null) {
            existing.setPaperType(paper.getPaperType());
        }
        if (paper.getStudentId() != null) {
            existing.setStudentId(paper.getStudentId());
        }
        if (paper.getStatus() != null) {
            existing.setStatus(paper.getStatus());
        }
        if (paper.getRemark() != null) {
            existing.setRemark(paper.getRemark());
        }
        paperMapper.updateById(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Paper existing = paperMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "试卷不存在");
        }
        paperQuestionMapper.delete(Wrappers.<PaperQuestion>lambdaQuery().eq(PaperQuestion::getPaperId, id));
        lessonPaperMapper.delete(Wrappers.<LessonPaper>lambdaQuery().eq(LessonPaper::getPaperId, id));
        paperMapper.deleteById(id);
    }

    @Override
    @Transactional
    public Long clone(Long id, Long studentId) {
        Paper src = paperMapper.selectById(id);
        if (src == null) {
            throw new BusinessException(404, "试卷不存在");
        }
        String stuName = null;
        if (studentId != null) {
            Student s = studentMapper.selectById(studentId);
            if (s == null) {
                throw new BusinessException(400, "所选学生不存在");
            }
            stuName = s.getName();
        }
        Paper np = new Paper();
        np.setTitle(src.getTitle() + (stuName == null ? "（副本）" : "（" + stuName + "）"));
        np.setGrade(src.getGrade());
        np.setPaperType(src.getPaperType());
        np.setStudentId(studentId);
        np.setParentPaperId(src.getId());
        np.setStatus("DRAFT");
        np.setRemark(src.getRemark());
        paperMapper.insert(np);

        List<PaperQuestion> links = paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, src.getId())
                .orderByAsc(PaperQuestion::getSortOrder)
                .orderByAsc(PaperQuestion::getId));
        for (PaperQuestion l : links) {
            Question sq = questionMapper.selectById(l.getQuestionId());
            if (sq == null) {
                continue;
            }
            // 题目整行复制（克隆语义：改派生卷不影响通用卷）
            Question nq = new Question();
            nq.setGrade(sq.getGrade());
            nq.setKpId(sq.getKpId());
            nq.setStudentId(studentId);
            nq.setQtype(sq.getQtype());
            nq.setStem(sq.getStem());
            nq.setOptions(sq.getOptions());
            nq.setAnswer(sq.getAnswer());
            nq.setAnalysis(sq.getAnalysis());
            nq.setDifficulty(sq.getDifficulty());
            nq.setSource(sq.getSource());
            questionMapper.insert(nq);

            PaperQuestion nl = new PaperQuestion();
            nl.setPaperId(np.getId());
            nl.setQuestionId(nq.getId());
            nl.setSortOrder(l.getSortOrder());
            paperQuestionMapper.insert(nl);
        }
        return np.getId();
    }

    @Override
    @Transactional
    public void setQuestions(PaperQuestionsSetDTO dto) {
        if (dto == null || dto.getPaperId() == null) {
            throw new BusinessException(400, "缺少试卷");
        }
        Paper paper = paperMapper.selectById(dto.getPaperId());
        if (paper == null) {
            throw new BusinessException(404, "试卷不存在");
        }
        paperQuestionMapper.delete(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, dto.getPaperId()));
        List<Long> ids = dto.getQuestionIds();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        int order = 0;
        for (Long qid : ids) {
            if (qid == null || !seen.add(qid)) {
                continue;
            }
            if (questionMapper.selectById(qid) == null) {
                continue;
            }
            PaperQuestion l = new PaperQuestion();
            l.setPaperId(dto.getPaperId());
            l.setQuestionId(qid);
            l.setSortOrder(++order);
            paperQuestionMapper.insert(l);
        }
    }

    @Override
    @Transactional
    public Long generate(PaperGenerateDTO dto) {
        if (dto == null || dto.getKpIds() == null || dto.getKpIds().isEmpty()) {
            throw new BusinessException(400, "请至少选择一个知识点");
        }
        int per = dto.getCountPerKp() == null || dto.getCountPerKp() <= 0 ? 5 : dto.getCountPerKp();
        String title = dto.getTitle();
        if (title == null || title.isBlank()) {
            title = (dto.getGrade() == null || dto.getGrade().isBlank() ? "" : dto.getGrade() + " ")
                    + "知识点专项练习";
        }
        Paper paper = new Paper();
        paper.setTitle(title);
        paper.setGrade(dto.getGrade());
        paper.setPaperType(dto.getPaperType() == null || dto.getPaperType().isBlank() ? "KP" : dto.getPaperType());
        paper.setStudentId(dto.getStudentId());
        paper.setStatus("DRAFT");
        paperMapper.insert(paper);

        int order = 0;
        List<Long> seen = new ArrayList<>();
        for (Long kpId : dto.getKpIds()) {
            if (kpId == null) {
                continue;
            }
            var qw = Wrappers.<Question>lambdaQuery()
                    .eq(Question::getKpId, kpId)
                    .isNull(Question::getStudentId);
            if (dto.getGrade() != null && !dto.getGrade().isBlank()) {
                qw.eq(Question::getGrade, dto.getGrade());
            }
            if (dto.getDifficulty() != null && dto.getDifficulty() > 0) {
                qw.eq(Question::getDifficulty, dto.getDifficulty());
            }
            List<Question> pool = questionMapper.selectList(qw);
            if (pool.isEmpty()) {
                continue;
            }
            Collections.shuffle(pool);
            int n = Math.min(per, pool.size());
            for (int i = 0; i < n; i++) {
                Long qid = pool.get(i).getId();
                if (seen.contains(qid)) {
                    continue;
                }
                seen.add(qid);
                PaperQuestion l = new PaperQuestion();
                l.setPaperId(paper.getId());
                l.setQuestionId(qid);
                l.setSortOrder(++order);
                paperQuestionMapper.insert(l);
            }
        }
        return paper.getId();
    }

    private void fillBasic(PaperVO vo, Paper p) {
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setGrade(p.getGrade());
        vo.setPaperType(p.getPaperType());
        vo.setStudentId(p.getStudentId());
        vo.setParentPaperId(p.getParentPaperId());
        vo.setStatus(p.getStatus());
        vo.setRemark(p.getRemark());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
    }

    private Map<Long, Long> countQuestionsByPaper(List<Long> paperIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (paperIds == null || paperIds.isEmpty()) {
            return counts;
        }
        List<PaperQuestion> links = paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                .select(PaperQuestion::getPaperId)
                .in(PaperQuestion::getPaperId, paperIds));
        for (PaperQuestion l : links) {
            counts.merge(l.getPaperId(), 1L, Long::sum);
        }
        return counts;
    }

    private Map<Long, String> nameMap(Set<Long> ids, boolean student) {
        Map<Long, String> m = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return m;
        }
        if (student) {
            for (Student s : studentMapper.selectBatchIds(ids)) {
                m.put(s.getId(), s.getName());
            }
        }
        return m;
    }

    private Map<Long, String> titleMap(Set<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return m;
        }
        for (Paper p : paperMapper.selectBatchIds(ids)) {
            m.put(p.getId(), p.getTitle());
        }
        return m;
    }

    /** 按题号顺序组装题目 VO。 */
    private List<QuestionVO> toQuestionVOs(List<Long> qIds) {
        List<QuestionVO> result = new ArrayList<>();
        if (qIds == null || qIds.isEmpty()) {
            return result;
        }
        List<Question> qs = questionMapper.selectBatchIds(qIds);
        Map<Long, Question> qm = new HashMap<>();
        Set<Long> kpIds = new HashSet<>();
        Set<Long> stuIds = new HashSet<>();
        for (Question q : qs) {
            qm.put(q.getId(), q);
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
        Map<Long, String> stuNames = nameMap(stuIds, true);
        for (Long qid : qIds) {
            Question q = qm.get(qid);
            if (q == null) {
                continue;
            }
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
