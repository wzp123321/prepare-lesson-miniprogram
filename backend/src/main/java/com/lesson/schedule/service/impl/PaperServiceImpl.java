package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.dto.KpCountDTO;
import com.lesson.schedule.common.dto.PaperGenerateCommitDTO;
import com.lesson.schedule.common.dto.PaperGenerateDTO;
import com.lesson.schedule.common.dto.PaperQueryDTO;
import com.lesson.schedule.common.dto.PaperQuestionsSetDTO;
import com.lesson.schedule.common.dto.PaperReuseDTO;
import com.lesson.schedule.common.vo.PaperDetailVO;
import com.lesson.schedule.common.vo.PaperGeneratePreviewVO;
import com.lesson.schedule.common.vo.PaperVO;
import com.lesson.schedule.common.vo.QuestionVO;
import com.lesson.schedule.entity.KnowledgePoint;
import com.lesson.schedule.entity.LessonPaper;
import com.lesson.schedule.entity.Paper;
import com.lesson.schedule.entity.PaperQuestion;
import com.lesson.schedule.entity.PaperQuestionOverride;
import com.lesson.schedule.entity.Question;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.mapper.KnowledgePointMapper;
import com.lesson.schedule.mapper.LessonPaperMapper;
import com.lesson.schedule.mapper.PaperMapper;
import com.lesson.schedule.mapper.PaperQuestionMapper;
import com.lesson.schedule.mapper.PaperQuestionOverrideMapper;
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
    private final PaperQuestionOverrideMapper paperQuestionOverrideMapper;
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
        vo.setQuestionCount((long) links.size());
        // 按编排顺序组装题目：有快照用快照，没有读题库原题
        vo.setQuestions(toQuestionVOsFromLinks(links));
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
            nl.setEdited(l.getEdited() == null ? 0 : l.getEdited());
            paperQuestionMapper.insert(nl);
            // 卷内编辑内容一并带走，保持派生卷卷面与原卷一致
            if (Integer.valueOf(1).equals(l.getEdited())) {
                copyOverride(l.getId(), nl.getId());
            }
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
        // 整体覆盖：先清旧编排及其内容覆盖（override 随编排项一起删）
        List<PaperQuestion> oldLinks = paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, dto.getPaperId()));
        for (PaperQuestion old : oldLinks) {
            paperQuestionOverrideMapper.delete(Wrappers.<PaperQuestionOverride>lambdaQuery()
                    .eq(PaperQuestionOverride::getPaperQuestionId, old.getId()));
        }
        paperQuestionMapper.delete(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, dto.getPaperId()));

        // 优先用 items（带卷内编辑覆盖）；否则退回 questionIds（全部视为未编辑）
        List<PaperQuestionsSetDTO.Item> items = new ArrayList<>();
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            items.addAll(dto.getItems());
        } else if (dto.getQuestionIds() != null) {
            for (Long qid : dto.getQuestionIds()) {
                PaperQuestionsSetDTO.Item it = new PaperQuestionsSetDTO.Item();
                it.setQuestionId(qid);
                items.add(it);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        int order = 0;
        for (PaperQuestionsSetDTO.Item it : items) {
            if (it == null || it.getQuestionId() == null || !seen.add(it.getQuestionId())) {
                continue;
            }
            if (questionMapper.selectById(it.getQuestionId()) == null) {
                continue;
            }
            PaperQuestion l = new PaperQuestion();
            l.setPaperId(dto.getPaperId());
            l.setQuestionId(it.getQuestionId());
            l.setSortOrder(++order);
            l.setEdited(Boolean.TRUE.equals(it.getEdited()) ? 1 : 0);
            paperQuestionMapper.insert(l);
            // 编辑过的题：内容单独落到 override 表（编排表不存内容）
            if (Integer.valueOf(1).equals(l.getEdited())) {
                PaperQuestionOverride ov = new PaperQuestionOverride();
                ov.setPaperQuestionId(l.getId());
                ov.setQtype(it.getQtype());
                ov.setStem(it.getStem());
                ov.setOptions(it.getOptions());
                ov.setAnswer(it.getAnswer());
                ov.setAnalysis(it.getAnalysis());
                ov.setDifficulty(it.getDifficulty());
                paperQuestionOverrideMapper.insert(ov);
            }
        }
    }

    @Override
    @Transactional
    public Long reuse(PaperReuseDTO dto) {
        if (dto == null || dto.getSourceId() == null) {
            throw new BusinessException(400, "请选择要复用的试卷");
        }
        Paper src = paperMapper.selectById(dto.getSourceId());
        if (src == null) {
            throw new BusinessException(404, "蓝本试卷不存在");
        }
        // 归属：显式传了就用传的，否则沿用蓝本卷（含 studentId=null 的通用卷）
        Long targetStudentId = dto.getStudentId() != null ? dto.getStudentId() : src.getStudentId();
        if (dto.getStudentId() != null && studentMapper.selectById(dto.getStudentId()) == null) {
            throw new BusinessException(400, "所选学生不存在");
        }
        String title = dto.getTitle() == null || dto.getTitle().isBlank()
                ? src.getTitle() + "（副本）"
                : dto.getTitle().trim();

        Paper np = new Paper();
        np.setTitle(title);
        np.setGrade(src.getGrade());
        np.setPaperType(src.getPaperType());
        np.setStudentId(targetStudentId);
        np.setParentPaperId(src.getId());
        np.setStatus("DRAFT");
        np.setRemark(src.getRemark());
        paperMapper.insert(np);

        // 复用编排：不克隆题库行，只复制引用顺序 + 卷内编辑快照
        List<PaperQuestion> links = paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, src.getId())
                .orderByAsc(PaperQuestion::getSortOrder)
                .orderByAsc(PaperQuestion::getId));
        for (PaperQuestion l : links) {
            // 只复制仍然存在的题，避免带入已被删除的题库行
            if (questionMapper.selectById(l.getQuestionId()) == null) {
                continue;
            }
            PaperQuestion npq = new PaperQuestion();
            npq.setPaperId(np.getId());
            npq.setQuestionId(l.getQuestionId());
            npq.setSortOrder(l.getSortOrder());
            npq.setEdited(l.getEdited() == null ? 0 : l.getEdited());
            paperQuestionMapper.insert(npq);
            // 卷内编辑内容一并复制到新卷
            if (Integer.valueOf(1).equals(l.getEdited())) {
                copyOverride(l.getId(), npq.getId());
            }
        }
        return np.getId();
    }

    @Override
    @Transactional
    public Long generate(PaperGenerateDTO dto) {
        assertGenerateParam(dto);
        Paper paper = new Paper();
        paper.setTitle(resolveTitle(dto));
        paper.setGrade(dto.getGrade());
        paper.setPaperType(resolvePaperType(dto));
        paper.setStudentId(dto.getStudentId());
        paper.setStatus("DRAFT");
        paperMapper.insert(paper);

        List<Long> picked = pickQuestionIds(dto);
        int order = 0;
        for (Long qid : picked) {
            PaperQuestion l = new PaperQuestion();
            l.setPaperId(paper.getId());
            l.setQuestionId(qid);
            l.setSortOrder(++order);
            paperQuestionMapper.insert(l);
        }
        return paper.getId();
    }

    @Override
    public PaperGeneratePreviewVO generatePreview(PaperGenerateDTO dto) {
        assertGenerateParam(dto);
        List<KpCountDTO> wants = normalizeCounts(dto);
        int maxTotal = dto.getMaxTotal() == null || dto.getMaxTotal() <= 0 ? Integer.MAX_VALUE : dto.getMaxTotal();

        List<Long> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<PaperGeneratePreviewVO.KpPickVO> picks = new ArrayList<>();
        Map<Long, String> kpNames = kpNameMap(wants);

        for (KpCountDTO want : wants) {
            PaperGeneratePreviewVO.KpPickVO pick = new PaperGeneratePreviewVO.KpPickVO();
            pick.setKpId(want.getKpId());
            pick.setKpName(kpNames.get(want.getKpId()));
            pick.setWanted(want.getCount());

            List<Question> pool = selectPool(dto, want.getKpId());
            pick.setAvailable(pool.size());
            if (pool.isEmpty()) {
                pick.setPicked(0);
                picks.add(pick);
                continue;
            }
            Collections.shuffle(pool);
            int picked = 0;
            for (Question q : pool) {
                if (picked >= want.getCount()) {
                    break;
                }
                if (ordered.size() >= maxTotal) {
                    break;
                }
                if (!seen.add(q.getId())) {
                    continue;
                }
                ordered.add(q.getId());
                picked++;
            }
            pick.setPicked(picked);
            picks.add(pick);
        }

        PaperGeneratePreviewVO vo = new PaperGeneratePreviewVO();
        vo.setQuestions(toQuestionVOs(ordered));
        vo.setTotal(ordered.size());
        vo.setPicks(picks);
        return vo;
    }

    @Override
    @Transactional
    public Long generateCommit(PaperGenerateCommitDTO dto) {
        if (dto == null || dto.getQuestionIds() == null || dto.getQuestionIds().isEmpty()) {
            throw new BusinessException(400, "组卷至少需要一道题");
        }
        Paper paper = new Paper();
        paper.setTitle(dto.getTitle() == null || dto.getTitle().isBlank()
                ? (dto.getGrade() == null || dto.getGrade().isBlank() ? "" : dto.getGrade() + " ") + "知识点专项练习"
                : dto.getTitle());
        paper.setGrade(dto.getGrade());
        paper.setPaperType(dto.getPaperType() == null || dto.getPaperType().isBlank() ? "KP" : dto.getPaperType());
        paper.setStudentId(dto.getStudentId());
        paper.setRemark(dto.getRemark());
        paper.setStatus("DRAFT");
        paperMapper.insert(paper);

        int order = 0;
        Set<Long> seen = new HashSet<>();
        for (Long qid : dto.getQuestionIds()) {
            if (qid == null || !seen.add(qid)) {
                continue;
            }
            if (questionMapper.selectById(qid) == null) {
                continue;
            }
            PaperQuestion l = new PaperQuestion();
            l.setPaperId(paper.getId());
            l.setQuestionId(qid);
            l.setSortOrder(++order);
            paperQuestionMapper.insert(l);
        }
        if (order == 0) {
            throw new BusinessException(400, "所选题目均已失效，请重新组卷");
        }
        return paper.getId();
    }

    private void assertGenerateParam(PaperGenerateDTO dto) {
        if (dto == null || dto.getKpIds() == null || dto.getKpIds().isEmpty()) {
            throw new BusinessException(400, "请至少选择一个知识点");
        }
    }

    /** 补齐题量：kpCounts 优先，否则用 countPerKp 平摊；过滤掉非正数量。 */
    private List<KpCountDTO> normalizeCounts(PaperGenerateDTO dto) {
        int fallback = dto.getCountPerKp() == null || dto.getCountPerKp() <= 0 ? 5 : dto.getCountPerKp();
        Map<Long, Integer> perKp = new HashMap<>();
        if (dto.getKpCounts() != null) {
            for (KpCountDTO kc : dto.getKpCounts()) {
                if (kc != null && kc.getKpId() != null && kc.getCount() != null && kc.getCount() > 0) {
                    perKp.put(kc.getKpId(), kc.getCount());
                }
            }
        }
        List<KpCountDTO> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Long kpId : dto.getKpIds()) {
            if (kpId == null || !used.add(kpId)) {
                continue;
            }
            KpCountDTO kc = new KpCountDTO();
            kc.setKpId(kpId);
            kc.setCount(perKp.containsKey(kpId) ? perKp.get(kpId) : fallback);
            result.add(kc);
        }
        return result;
    }

    /** 按知识点查候选池（通用题 + 年级/难度过滤）。 */
    private List<Question> selectPool(PaperGenerateDTO dto, Long kpId) {
        var qw = Wrappers.<Question>lambdaQuery()
                .eq(Question::getKpId, kpId)
                .isNull(Question::getStudentId);
        if (dto.getGrade() != null && !dto.getGrade().isBlank()) {
            qw.eq(Question::getGrade, dto.getGrade());
        }
        if (dto.getDifficulty() != null && dto.getDifficulty() > 0) {
            qw.eq(Question::getDifficulty, dto.getDifficulty());
        }
        return questionMapper.selectList(qw);
    }

    /** 试抽共用的取题逻辑：按点设量、去重、受 maxTotal 约束，返回题号顺序。 */
    private List<Long> pickQuestionIds(PaperGenerateDTO dto) {
        int maxTotal = dto.getMaxTotal() == null || dto.getMaxTotal() <= 0 ? Integer.MAX_VALUE : dto.getMaxTotal();
        List<Long> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (KpCountDTO want : normalizeCounts(dto)) {
            List<Question> pool = selectPool(dto, want.getKpId());
            if (pool.isEmpty()) {
                continue;
            }
            Collections.shuffle(pool);
            int picked = 0;
            for (Question q : pool) {
                if (picked >= want.getCount() || ordered.size() >= maxTotal) {
                    break;
                }
                if (seen.add(q.getId())) {
                    ordered.add(q.getId());
                    picked++;
                }
            }
        }
        return ordered;
    }

    private String resolveTitle(PaperGenerateDTO dto) {
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            return dto.getTitle();
        }
        return (dto.getGrade() == null || dto.getGrade().isBlank() ? "" : dto.getGrade() + " ") + "知识点专项练习";
    }

    private String resolvePaperType(PaperGenerateDTO dto) {
        return dto.getPaperType() == null || dto.getPaperType().isBlank() ? "KP" : dto.getPaperType();
    }

    private Map<Long, String> kpNameMap(List<KpCountDTO> wants) {
        Set<Long> ids = new HashSet<>();
        for (KpCountDTO kc : wants) {
            if (kc.getKpId() != null) {
                ids.add(kc.getKpId());
            }
        }
        Map<Long, String> m = new HashMap<>();
        if (!ids.isEmpty()) {
            for (KnowledgePoint kp : knowledgePointMapper.selectBatchIds(ids)) {
                m.put(kp.getId(), kp.getName());
            }
        }
        return m;
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

    /**
     * 按编排行组装题目 VO —— 卷内编辑语义的唯一出口。
     * <p>{@code edited=1} 时，题干/选项/答案/解析/难度/题型取 {@code paper_question_override} 表；
     * 未编辑（或 override 行意外缺失，双保险）则回落 {@code question} 表原题。
     * 知识点名、年级、来源这些"归档属性"始终取原题（卷内编辑只改卷面呈现，不改编目归属）。
     */
    private List<QuestionVO> toQuestionVOsFromLinks(List<PaperQuestion> links) {
        List<QuestionVO> result = new ArrayList<>();
        if (links == null || links.isEmpty()) {
            return result;
        }
        List<Long> qIds = new ArrayList<>();
        List<Long> linkIds = new ArrayList<>();
        for (PaperQuestion l : links) {
            qIds.add(l.getQuestionId());
            linkIds.add(l.getId());
        }
        // 一次把原题与"卷内编辑覆盖"都查出来，避免 N+1
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
        Map<Long, PaperQuestionOverride> ovm = new HashMap<>();
        if (!linkIds.isEmpty()) {
            for (PaperQuestionOverride ov : paperQuestionOverrideMapper.selectList(
                    Wrappers.<PaperQuestionOverride>lambdaQuery()
                            .in(PaperQuestionOverride::getPaperQuestionId, linkIds))) {
                ovm.put(ov.getPaperQuestionId(), ov);
            }
        }
        Map<Long, String> kpNames = new HashMap<>();
        if (!kpIds.isEmpty()) {
            for (KnowledgePoint kp : knowledgePointMapper.selectBatchIds(kpIds)) {
                kpNames.put(kp.getId(), kp.getName());
            }
        }
        Map<Long, String> stuNames = nameMap(stuIds, true);

        for (PaperQuestion l : links) {
            Question q = qm.get(l.getQuestionId());
            if (q == null) {
                continue;
            }
            PaperQuestionOverride ov = Integer.valueOf(1).equals(l.getEdited()) ? ovm.get(l.getId()) : null;
            boolean edited = ov != null;
            QuestionVO vo = new QuestionVO();
            vo.setId(q.getId());
            vo.setGrade(q.getGrade());
            vo.setKpId(q.getKpId());
            vo.setKpName(q.getKpId() == null ? null : kpNames.get(q.getKpId()));
            vo.setStudentId(q.getStudentId());
            vo.setStudentName(q.getStudentId() == null ? null : stuNames.get(q.getStudentId()));
            vo.setSource(q.getSource());
            vo.setCreateTime(q.getCreateTime());
            vo.setUpdateTime(q.getUpdateTime());
            vo.setEdited(edited);
            if (edited) {
                vo.setQtype(ov.getQtype() != null ? ov.getQtype() : q.getQtype());
                vo.setStem(ov.getStem() != null ? ov.getStem() : q.getStem());
                vo.setOptions(ov.getOptions());
                vo.setAnswer(ov.getAnswer());
                vo.setAnalysis(ov.getAnalysis());
                vo.setDifficulty(ov.getDifficulty() != null ? ov.getDifficulty() : q.getDifficulty());
            } else {
                vo.setQtype(q.getQtype());
                vo.setStem(q.getStem());
                vo.setOptions(q.getOptions());
                vo.setAnswer(q.getAnswer());
                vo.setAnalysis(q.getAnalysis());
                vo.setDifficulty(q.getDifficulty());
            }
            result.add(vo);
        }
        return result;
    }

    /** 把一条编排项的卷内编辑内容复制到另一条（clone / reuse 用）。 */
    private void copyOverride(Long fromPaperQuestionId, Long toPaperQuestionId) {
        PaperQuestionOverride src = paperQuestionOverrideMapper.selectOne(
                Wrappers.<PaperQuestionOverride>lambdaQuery()
                        .eq(PaperQuestionOverride::getPaperQuestionId, fromPaperQuestionId));
        if (src == null) {
            return;
        }
        PaperQuestionOverride dst = new PaperQuestionOverride();
        dst.setPaperQuestionId(toPaperQuestionId);
        dst.setQtype(src.getQtype());
        dst.setStem(src.getStem());
        dst.setOptions(src.getOptions());
        dst.setAnswer(src.getAnswer());
        dst.setAnalysis(src.getAnalysis());
        dst.setDifficulty(src.getDifficulty());
        paperQuestionOverrideMapper.insert(dst);
    }
}
