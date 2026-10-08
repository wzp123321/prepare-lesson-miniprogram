package com.lesson.schedule.controller;

import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.Result;
import com.lesson.schedule.common.dto.IdDTO;
import com.lesson.schedule.common.dto.StudentQueryDTO;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生管理（S01）。路径对齐概设 §2.4.1 BE-API-01~05。
 * 统一 POST + 动作化路径，参数全部走 {@code @RequestBody}（路径不含任何变量）。
 */
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /** BE-API-01 列表+搜索（分页）。入参：keyword / grade / status / page / size。 */
    @PostMapping("/list")
    public Result<PageResult<Student>> list(@RequestBody StudentQueryDTO dto) {
        return Result.success(studentService.listStudents(
                dto.getKeyword(), dto.getGrade(), dto.getStatus(), dto.getPage(), dto.getSize()));
    }

    /** BE-API-02 详情（含 pendingMakeUpCount）。入参：id。 */
    @PostMapping("/detail")
    public Result<Student> detail(@RequestBody IdDTO dto) {
        return Result.success(studentService.getDetail(dto.getId()));
    }

    /** BE-API-03 新增（color 可自动分配）。入参：学生各字段。 */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody Student student) {
        return Result.success(studentService.createStudent(student));
    }

    /** BE-API-04 修改（部分字段）。入参：id + 学生各字段。 */
    @PostMapping("/update")
    public Result<Void> update(@RequestBody Student student) {
        studentService.updateStudent(student.getId(), student);
        return Result.success();
    }

    /** BE-API-05 删除（删除保护，冲突 409）。入参：id。 */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody IdDTO dto) {
        studentService.deleteStudent(dto.getId());
        return Result.success();
    }

    /** 暂停归档（status=0），删除保护提示的替代动作。入参：id。 */
    @PostMapping("/pause")
    public Result<Void> pause(@RequestBody IdDTO dto) {
        studentService.pause(dto.getId());
        return Result.success();
    }
}
