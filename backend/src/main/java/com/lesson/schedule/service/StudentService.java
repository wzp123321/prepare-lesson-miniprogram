package com.lesson.schedule.service;

import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.vo.StudentMonthScheduleVO;
import com.lesson.schedule.entity.Student;

/**
 * 学生服务（S01）。含搜索分页、详情（含待补课数）、新增（颜色自动分配）、
 * 部分字段修改、删除保护、暂停归档。核心域规则写在 service，不散落 controller。
 */
public interface StudentService {

    /** BE-API-01 列表+搜索（keyword/grade/status 分页）。 */
    PageResult<Student> listStudents(String keyword, String grade, Integer status, long page, long size);

    /** BE-API-02 详情，联 lesson 计 ABSENT&closed=0 写入 pendingMakeUpCount。 */
    Student getDetail(Long id);

    /** BE-API-03 新增（color 可自动分配）。返回生成主键。 */
    Long createStudent(Student student);

    /** BE-API-04 部分字段修改。 */
    void updateStudent(Long id, Student student);

    /** BE-API-05 删除保护：存在未结课程或未来排课时抛 409。 */
    void deleteStudent(Long id);

    /** 暂停归档（status=0），删除保护提示的替代动作。 */
    void pause(Long id);

    /** BE-API-20 按学生当月课表出图数据（studentInfo + 课表列表）。 */
    StudentMonthScheduleVO getStudentMonthSchedule(Long studentId, int year, int month);
}
