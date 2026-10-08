package com.lesson.schedule.service;

import com.lesson.schedule.entity.Course;

/**
 * 课程服务（S03）。每生一门语文课，price 冗余自 student.price；停用保留历史。
 */
public interface CourseService {

    /** BE-API-11 查该生语文课（无则 null）。 */
    Course getByStudent(Long studentId);

    /** BE-API-12 建（每生一门，重复 409）。返回生成主键。 */
    Long createCourse(Long studentId, String remark);

    /** BE-API-13 修改备注。 */
    void updateRemark(Long id, String remark);

    /** BE-API-14 停用（enabled=0，保留历史）。 */
    void disable(Long id);
}
