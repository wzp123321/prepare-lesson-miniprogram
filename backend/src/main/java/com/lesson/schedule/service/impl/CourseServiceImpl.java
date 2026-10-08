package com.lesson.schedule.service.impl;

import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.entity.Course;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.mapper.CourseMapper;
import com.lesson.schedule.mapper.StudentMapper;
import com.lesson.schedule.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;

    @Override
    public Course getByStudent(Long studentId) {
        return courseMapper.selectByStudentId(studentId);
    }

    @Override
    public Long createCourse(Long studentId, String remark) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学生不存在");
        }
        int count = courseMapper.countByStudentId(studentId);
        if (count > 0) {
            throw new BusinessException(409, "该学生已存在语文课程（每生一门）");
        }
        Course course = new Course();
        course.setStudentId(studentId);
        course.setSubject("语文");
        course.setPrice(student.getPrice());
        course.setRemark(remark);
        course.setEnabled(1);
        courseMapper.insert(course);
        return course.getId();
    }

    @Override
    public void updateRemark(Long id, String remark) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        course.setRemark(remark);
        courseMapper.updateById(course);
    }

    @Override
    public void disable(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        // 停用仅置 enabled=0，保留历史
        course.setEnabled(0);
        courseMapper.updateById(course);
    }

    @Override
    public void enable(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        course.setEnabled(1);
        courseMapper.updateById(course);
    }
}
