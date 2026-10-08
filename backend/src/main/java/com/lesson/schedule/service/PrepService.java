package com.lesson.schedule.service;

import com.lesson.schedule.common.dto.PrepQueryDTO;
import com.lesson.schedule.common.dto.PrepSaveDTO;
import com.lesson.schedule.common.vo.LessonPrepVO;
import com.lesson.schedule.common.vo.QuestionVO;
import com.lesson.schedule.common.vo.TodoPrepVO;
import java.util.List;

/** 备课服务（课次 → 知识点 / 试卷）。 */
public interface PrepService {

    /** 待备课列表：区间内课次 + 已配知识点 / 试卷 + 备课状态。 */
    List<TodoPrepVO> todo(PrepQueryDTO query);

    /** 单节课备课详情。 */
    LessonPrepVO get(Long lessonId);

    /** 保存某节课的备课安排（整体覆盖）。 */
    void save(PrepSaveDTO dto);

    /** 按该课已选知识点推荐可用题目（跨卷取题）。 */
    List<QuestionVO> suggest(Long lessonId);
}
