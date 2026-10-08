package com.lesson.schedule.controller;

import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.Result;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生管理（S01）。路径对齐概设 §2.4.1 BE-API-01~05。
 */
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /** BE-API-01 列表+搜索（分页 {list, total, page, size}）。 */
    @GetMapping
    public Result<PageResult<Student>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(studentService.listStudents(keyword, grade, status, page, size));
    }

    /** BE-API-02 详情（含 pendingMakeUpCount）。 */
    @GetMapping("/{id}")
    public Result<Student> detail(@PathVariable Long id) {
        return Result.success(studentService.getDetail(id));
    }

    /** BE-API-03 新增（color 可自动分配）。 */
    @PostMapping
    public Result<Long> create(@RequestBody Student student) {
        return Result.success(studentService.createStudent(student));
    }

    /** BE-API-04 修改（部分字段）。 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Student student) {
        studentService.updateStudent(id, student);
        return Result.success();
    }

    /** BE-API-05 删除（删除保护，冲突 409）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return Result.success();
    }

    /** 暂停归档（status=0），删除保护提示的替代动作。 */
    @PutMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id) {
        studentService.pause(id);
        return Result.success();
    }
}
