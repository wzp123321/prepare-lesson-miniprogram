package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.dto.PrepQueryDTO;
import com.lesson.schedule.common.dto.PrepSaveDTO;
import com.lesson.schedule.common.vo.LessonPrepVO;
import com.lesson.schedule.common.vo.QuestionVO;
import com.lesson.schedule.common.vo.TodoPrepVO;
import com.lesson.schedule.entity.KnowledgePoint;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.entity.LessonKnowledge;
import com.lesson.schedule.entity.LessonPaper;
import com.lesson.schedule.entity.Paper;
import com.lesson.schedule.entity.Question;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.mapper.KnowledgePointMapper;
import com.lesson.schedule.mapper.LessonKnowledgeMapper;
import com.lesson.schedule.mapper.LessonMapper;
import com.lesson.schedule.mapper.LessonPaperMapper;
import com.lesson.schedule.mapper.PaperMapper;
import com.lesson.schedule.mapper.QuestionMapper;
import com.lesson.schedule.mapper.StudentMapper;
import com.lesson.schedule.mapper.TimeSlotMapper;
import com.lesson.schedule.service.PrepService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrepServiceImpl implements PrepService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final LessonMapper lessonMapper;
    private final StudentMapper studentMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final PaperMapper paperMapper;
    private final LessonKnowledgeMapper lessonKnowledgeMapper;
    private final LessonPaperMapper lessonPaperMapper;
    private final QuestionMapper questionMapper;

    @Override
    public List<TodoPrepVO> todo(PrepQueryDTO query) {
        LocalDate from = query != null && query.getFrom() != null ? query.getFrom() : LocalDate.now();
        LocalDate to = query != null && query.getTo() != null ? query.getTo() : from.plusDays(6);
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .ge(Lesson::getLessonDate, from)
                .le(Lesson::getLessonDate, to)
                .orderByAsc(Lesson::getLessonDate)
                .orderByAsc(Lesson::getSlotId));
        List<TodoPrepVO> result = new ArrayList<>();
        if (lessons.isEmpty()) {
            return result;
        }

        List<Long> lessonIds = new ArrayList<>();
        Set<Long> stuIds = new HashSet<>();
        Set<Long> slotIds = new HashSet<>();
        for (Lesson l : lessons) {
            lessonIds.add(l.getId());
            if (l.getStudentId() != null) {
                stuIds.add(l.getStudentId());
            }
            if (l.getSlotId() != null) {
                slotIds.add(l.getSlotId());
            }
        }

        Map<Long, String> stuNames = new HashMap<>();
        Map<Long, String> stuGrades = new HashMap<>();
        if (!stuIds.isEmpty()) {
            for (Student s : studentMapper.selectBatchIds(stuIds)) {
                stuNames.put(s.getId(), s.getName());
                stuGrades.put(s.getId(), s.getGrade());
            }
        }
        Map<Long, TimeSlot> slotMap = new HashMap<>();
        if (!slotIds.isEmpty()) {
            for (TimeSlot t : timeSlotMapper.selectBatchIds(slotIds)) {
                slotMap.put(t.getId(), t);
            }
        }

        // 课次 → 知识点 / 试卷
        Map<Long, List<Long>> kpMap = new HashMap<>();
        Set<Long> allKpIds = new HashSet<>();
        for (LessonKnowledge lk : lessonKnowledgeMapper.selectList(Wrappers.<LessonKnowledge>lambdaQuery()
                .in(LessonKnowledge::getLessonId, lessonIds))) {
            kpMap.computeIfAbsent(lk.getLessonId(), k -> new ArrayList<>()).add(lk.getKpId());
            allKpIds.add(lk.getKpId());
        }
        Map<Long, List<Long>> paperMap = new HashMap<>();
        Set<Long> allPaperIds = new HashSet<>();
        for (LessonPaper lp : lessonPaperMapper.selectList(Wrappers.<LessonPaper>lambdaQuery()
                .in(LessonPaper::getLessonId, lessonIds))) {
            paperMap.computeIfAbsent(lp.getLessonId(), k -> new ArrayList<>()).add(lp.getPaperId());
            allPaperIds.add(lp.getPaperId());
        }

        Map<Long, String> kpNames = new HashMap<>();
        if (!allKpIds.isEmpty()) {
            for (KnowledgePoint kp : knowledgePointMapper.selectBatchIds(allKpIds)) {
                kpNames.put(kp.getId(), kp.getName());
            }
        }
        Map<Long, String> paperTitles = new HashMap<>();
        if (!allPaperIds.isEmpty()) {
            for (Paper p : paperMapper.selectBatchIds(allPaperIds)) {
                paperTitles.put(p.getId(), p.getTitle());
            }
        }

        for (Lesson l : lessons) {
            TodoPrepVO vo = new TodoPrepVO();
            vo.setLessonId(l.getId());
            vo.setLessonDate(l.getLessonDate());
            vo.setSlotId(l.getSlotId());
            TimeSlot slot = l.getSlotId() == null ? null : slotMap.get(l.getSlotId());
            if (slot != null) {
                vo.setSlotStart(slot.getStartTime() == null ? null : HH_MM.format(slot.getStartTime()));
                vo.setSlotEnd(slot.getEndTime() == null ? null : HH_MM.format(slot.getEndTime()));
            }
            vo.setStudentId(l.getStudentId());
            vo.setStudentName(l.getStudentId() == null ? null : stuNames.get(l.getStudentId()));
            vo.setGrade(l.getStudentId() == null ? null : stuGrades.get(l.getStudentId()));
            vo.setLessonStatus(l.getStatus());
            vo.setRemark(l.getPrepRemark());

            List<Long> kids = kpMap.getOrDefault(l.getId(), new ArrayList<>());
            List<Long> pids = paperMap.getOrDefault(l.getId(), new ArrayList<>());
            vo.setKpIds(kids);
            vo.setPaperIds(pids);
            vo.setKpNames(kids.stream().map(kpNames::get).filter(Objects::nonNull).toList());
            vo.setPaperTitles(pids.stream().map(paperTitles::get).filter(Objects::nonNull).toList());
            vo.setPrepared(!kids.isEmpty());
            result.add(vo);
        }
        return result;
    }

    @Override
    public LessonPrepVO get(Long lessonId) {
        if (lessonId == null) {
            throw new BusinessException(400, "缺少课次");
        }
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在");
        }
        LessonPrepVO vo = new LessonPrepVO();
        vo.setLessonId(lessonId);
        vo.setStudentId(lesson.getStudentId());
        vo.setLessonDate(lesson.getLessonDate());
        vo.setRemark(lesson.getPrepRemark());
        if (lesson.getStudentId() != null) {
            Student s = studentMapper.selectById(lesson.getStudentId());
            if (s != null) {
                vo.setStudentName(s.getName());
                vo.setGrade(s.getGrade());
            }
        }
        vo.setKpIds(kpIdsOf(lessonId));
        vo.setPaperIds(paperIdsOf(lessonId));
        return vo;
    }

    @Override
    @Transactional
    public void save(PrepSaveDTO dto) {
        if (dto == null || dto.getLessonId() == null) {
            throw new BusinessException(400, "缺少课次");
        }
        Lesson lesson = lessonMapper.selectById(dto.getLessonId());
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在");
        }
        // 知识点整体覆盖
        lessonKnowledgeMapper.delete(Wrappers.<LessonKnowledge>lambdaQuery()
                .eq(LessonKnowledge::getLessonId, dto.getLessonId()));
        if (dto.getKpIds() != null) {
            Set<Long> seen = new HashSet<>();
            for (Long kpId : dto.getKpIds()) {
                if (kpId == null || !seen.add(kpId)) {
                    continue;
                }
                LessonKnowledge lk = new LessonKnowledge();
                lk.setLessonId(dto.getLessonId());
                lk.setKpId(kpId);
                lessonKnowledgeMapper.insert(lk);
            }
        }
        // 试卷整体覆盖
        lessonPaperMapper.delete(Wrappers.<LessonPaper>lambdaQuery()
                .eq(LessonPaper::getLessonId, dto.getLessonId()));
        if (dto.getPaperIds() != null) {
            Set<Long> seen = new HashSet<>();
            for (Long paperId : dto.getPaperIds()) {
                if (paperId == null || !seen.add(paperId)) {
                    continue;
                }
                LessonPaper lp = new LessonPaper();
                lp.setLessonId(dto.getLessonId());
                lp.setPaperId(paperId);
                lessonPaperMapper.insert(lp);
            }
        }
        // 备课备注落在 lesson.prep_remark（与排课备注 remark 分开，避免抢同一列）
        if (dto.getRemark() != null) {
            lesson.setPrepRemark(dto.getRemark());
            lessonMapper.updateById(lesson);
        }
    }

    @Override
    public List<QuestionVO> suggest(Long lessonId) {
        if (lessonId == null) {
            throw new BusinessException(400, "缺少课次");
        }
        List<Long> kpIds = kpIdsOf(lessonId);
        if (kpIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Question> qs = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .in(Question::getKpId, kpIds)
                .isNull(Question::getStudentId)
                .orderByDesc(Question::getId)
                .last("LIMIT 100"));
        List<QuestionVO> result = new ArrayList<>();
        if (qs.isEmpty()) {
            return result;
        }
        Set<Long> kpIdSet = new HashSet<>();
        for (Question q : qs) {
            if (q.getKpId() != null) {
                kpIdSet.add(q.getKpId());
            }
        }
        Map<Long, String> kpNames = new HashMap<>();
        if (!kpIdSet.isEmpty()) {
            for (KnowledgePoint kp : knowledgePointMapper.selectBatchIds(kpIdSet)) {
                kpNames.put(kp.getId(), kp.getName());
            }
        }
        for (Question q : qs) {
            QuestionVO vo = new QuestionVO();
            vo.setId(q.getId());
            vo.setGrade(q.getGrade());
            vo.setKpId(q.getKpId());
            vo.setKpName(q.getKpId() == null ? null : kpNames.get(q.getKpId()));
            vo.setQtype(q.getQtype());
            vo.setStem(q.getStem());
            vo.setOptions(q.getOptions());
            vo.setAnswer(q.getAnswer());
            vo.setAnalysis(q.getAnalysis());
            vo.setDifficulty(q.getDifficulty());
            vo.setSource(q.getSource());
            result.add(vo);
        }
        return result;
    }

    private List<Long> kpIdsOf(Long lessonId) {
        List<Long> ids = new ArrayList<>();
        for (LessonKnowledge lk : lessonKnowledgeMapper.selectList(Wrappers.<LessonKnowledge>lambdaQuery()
                .eq(LessonKnowledge::getLessonId, lessonId))) {
            ids.add(lk.getKpId());
        }
        return ids;
    }

    private List<Long> paperIdsOf(Long lessonId) {
        List<Long> ids = new ArrayList<>();
        for (LessonPaper lp : lessonPaperMapper.selectList(Wrappers.<LessonPaper>lambdaQuery()
                .eq(LessonPaper::getLessonId, lessonId))) {
            ids.add(lp.getPaperId());
        }
        return ids;
    }
}
