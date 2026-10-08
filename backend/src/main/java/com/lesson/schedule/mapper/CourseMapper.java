package com.lesson.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lesson.schedule.entity.Course;
import org.apache.ibatis.annotations.Param;

/**
 * 课程 Mapper（S03，每生一门语文课）。基础 CRUD 由 MyBatis-Plus 提供，
 * 补充「按 student_id 查唯一」与唯一性校验（A-14）。
 */
public interface CourseMapper extends BaseMapper<Course> {

    /** 查某学生的语文课（每生唯一一条），无则 null。 */
    Course selectByStudentId(@Param("studentId") Long studentId);

    /** 统计某学生的课程数（唯一性 / 引用计数用）。 */
    int countByStudentId(@Param("studentId") Long studentId);
}
