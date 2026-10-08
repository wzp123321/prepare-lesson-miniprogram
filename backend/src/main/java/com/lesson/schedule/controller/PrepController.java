package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.LessonIdDTO;
import com.lesson.schedule.common.dto.PrepQueryDTO;
import com.lesson.schedule.common.dto.PrepSaveDTO;
import com.lesson.schedule.common.vo.LessonPrepVO;
import com.lesson.schedule.common.vo.QuestionVO;
import com.lesson.schedule.common.vo.TodoPrepVO;
import com.lesson.schedule.service.PrepService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 备课（备课模块 BE-L-01~04）：课次 ↔ 知识点 / 试卷。
 * 统一 POST + 动作化路径，参数全部走 {@code @RequestBody}。
 */
@RestController
@RequestMapping("/api/prep")
@RequiredArgsConstructor
public class PrepController {

    private final PrepService prepService;

    /** BE-L-01 待备课列表。入参：from / to。 */
    @PostMapping("/todo")
    public Result<List<TodoPrepVO>> todo(@RequestBody PrepQueryDTO dto) {
        return Result.success(prepService.todo(dto));
    }

    /** BE-L-02 单节课备课详情。入参：lessonId。 */
    @PostMapping("/get")
    public Result<LessonPrepVO> get(@RequestBody LessonIdDTO dto) {
        return Result.success(prepService.get(dto.getLessonId()));
    }

    /** BE-L-03 保存备课安排（整体覆盖）。入参：lessonId / kpIds / paperIds / remark。 */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody PrepSaveDTO dto) {
        prepService.save(dto);
        return Result.success();
    }

    /** BE-L-04 按已选知识点推荐可用题目。入参：lessonId。 */
    @PostMapping("/suggest")
    public Result<List<QuestionVO>> suggest(@RequestBody LessonIdDTO dto) {
        return Result.success(prepService.suggest(dto.getLessonId()));
    }
}
