package com.lesson.schedule.controller;

import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.CourseCreateDTO;
import com.lesson.schedule.entity.Course;
import com.lesson.schedule.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程管理（S03）。路径对齐概设 §2.4.3 BE-API-11~14。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /** BE-API-11 查该生语文课（无则 data=null）。 */
    @GetMapping("/students/{studentId}/course")
    public Result<Course> getByStudent(@PathVariable Long studentId) {
        return Result.success(courseService.getByStudent(studentId));
    }

    /** BE-API-12 建（每生一门，重复 409；price 自动取自 student.price）。 */
    @PostMapping("/students/{studentId}/course")
    public Result<Long> create(@PathVariable Long studentId, @RequestBody CourseCreateDTO dto) {
        return Result.success(courseService.createCourse(studentId, dto.getRemark()));
    }

    /** BE-API-13 修改备注。 */
    @PutMapping("/courses/{id}")
    public Result<Void> updateRemark(@PathVariable Long id, @RequestBody CourseCreateDTO dto) {
        courseService.updateRemark(id, dto.getRemark());
        return Result.success();
    }

    /** BE-API-14 停用（enabled=0，保留历史）。 */
    @PutMapping("/courses/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        courseService.disable(id);
        return Result.success();
    }
}
