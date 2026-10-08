package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.CourseCreateDTO;
import com.lesson.schedule.common.dto.CourseQueryDTO;
import com.lesson.schedule.common.dto.IdDTO;
import com.lesson.schedule.entity.Course;
import com.lesson.schedule.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程管理（S03）。路径对齐概设 §2.4.3 BE-API-11~14。统一 POST + 动作化路径。
 * 每生一门语文课；price 由后端冗余自 student.price；subject 固定「语文」。
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /** BE-API-11 查该生语文课（无则 data=null）。入参：studentId。 */
    @PostMapping("/by-student")
    public Result<Course> getByStudent(@RequestBody CourseQueryDTO dto) {
        return Result.success(courseService.getByStudent(dto.getStudentId()));
    }

    /** BE-API-12 建（每生一门，重复 409；price 自动取自 student.price）。入参：studentId + remark。 */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody CourseCreateDTO dto) {
        return Result.success(courseService.createCourse(dto.getStudentId(), dto.getRemark()));
    }

    /** BE-API-13 修改备注。入参：id + remark。 */
    @PostMapping("/update-remark")
    public Result<Void> updateRemark(@RequestBody CourseCreateDTO dto) {
        courseService.updateRemark(dto.getId(), dto.getRemark());
        return Result.success();
    }

    /** BE-API-14 停用（enabled=0，保留历史）。入参：id。 */
    @PostMapping("/disable")
    public Result<Void> disable(@RequestBody IdDTO dto) {
        courseService.disable(dto.getId());
        return Result.success();
    }

    /** 启用（enabled=1）。入参：id。 */
    @PostMapping("/enable")
    public Result<Void> enable(@RequestBody IdDTO dto) {
        courseService.enable(dto.getId());
        return Result.success();
    }
}
