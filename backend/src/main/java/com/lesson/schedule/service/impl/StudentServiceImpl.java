package com.lesson.schedule.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.PageResult;
import com.lesson.schedule.common.vo.StudentInfoVO;
import com.lesson.schedule.common.vo.StudentLessonCellVO;
import com.lesson.schedule.common.vo.StudentMonthScheduleVO;
import com.lesson.schedule.entity.Course;
import com.lesson.schedule.entity.Lesson;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.mapper.CourseMapper;
import com.lesson.schedule.mapper.LessonMapper;
import com.lesson.schedule.mapper.StudentMapper;
import com.lesson.schedule.service.StudentService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    /** 专属着色调色板（前端课表着色用）。 */
    private static final List<String> COLOR_PALETTE = Arrays.asList(
            "#F56C6C", "#E6A23C", "#67C23A", "#409EFF",
            "#9B59B6", "#FF7F50", "#1ABC9C", "#34495E");

    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final LessonMapper lessonMapper;

    @Override
    public PageResult<Student> listStudents(String keyword, String grade, Integer status, long page, long size) {
        Page<Student> p = new Page<>(page, size);
        var qw = Wrappers.<Student>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Student::getName, keyword).or().like(Student::getPhone, keyword);
        }
        if (grade != null && !grade.isBlank()) {
            qw.eq(Student::getGrade, grade);
        }
        if (status != null) {
            qw.eq(Student::getStatus, status);
        }
        qw.orderByDesc(Student::getId);
        Page<Student> result = studentMapper.selectPage(p, qw);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public Student getDetail(Long id) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new BusinessException(404, "学生不存在");
        }
        long pending = lessonMapper.selectCount(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getStudentId, id)
                .eq(Lesson::getStatus, "ABSENT")
                .eq(Lesson::getClosed, 0));
        student.setPendingMakeUpCount((int) pending);
        return student;
    }

    @Override
    public Long createStudent(Student student) {
        if (student.getName() == null || student.getName().isBlank()) {
            throw new BusinessException(400, "姓名不能为空");
        }
        if (student.getAddress() == null || student.getAddress().isBlank()) {
            throw new BusinessException(400, "家庭地址不能为空");
        }
        if (student.getStatus() == null) {
            student.setStatus(1);
        }
        if (student.getColor() == null || student.getColor().isBlank()) {
            student.setColor(allocateColor());
        }
        // 主键交给数据库自增，避免覆盖 AUTO_INCREMENT
        student.setId(null);
        studentMapper.insert(student);
        return student.getId();
    }

    @Override
    public void updateStudent(Long id, Student student) {
        Student existing = studentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "学生不存在");
        }
        applyPartial(existing, student);
        studentMapper.updateById(existing);
    }

    @Override
    public void deleteStudent(Long id) {
        Student existing = studentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "学生不存在");
        }
        // 删除保护：未结课程（enabled=1）或未来排课存在时禁止硬删
        Course course = courseMapper.selectByStudentId(id);
        boolean hasActiveCourse = course != null && (course.getEnabled() == null || course.getEnabled() == 1);
        long futureLessons = lessonMapper.selectCount(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getStudentId, id)
                .ge(Lesson::getLessonDate, LocalDate.now()));
        if (hasActiveCourse || futureLessons > 0) {
            throw new BusinessException(409, "该学生存在未结课程或未来排课，请先暂停归档");
        }
        studentMapper.deleteById(id);
    }

    @Override
    public void pause(Long id) {
        Student existing = studentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "学生不存在");
        }
        existing.setStatus(0);
        studentMapper.updateById(existing);
    }

    @Override
    public StudentMonthScheduleVO getStudentMonthSchedule(Long studentId, int year, int month) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学生不存在：id=" + studentId);
        }
        StudentInfoVO info = new StudentInfoVO();
        info.setName(student.getName());
        info.setColor(student.getColor());
        info.setGrade(student.getGrade());
        info.setPrice(student.getPrice());

        List<StudentLessonCellVO> lessons = lessonMapper.selectStudentMonthSchedule(studentId, year, month);
        if (lessons == null) {
            lessons = new ArrayList<>();
        }
        StudentMonthScheduleVO vo = new StudentMonthScheduleVO();
        vo.setStudentInfo(info);
        vo.setLessons(lessons);
        return vo;
    }

    /** 部分字段更新：仅覆盖请求中非 null 的字段。 */
    private void applyPartial(Student target, Student src) {
        if (src.getName() != null) {
            target.setName(src.getName());
        }
        if (src.getGrade() != null) {
            target.setGrade(src.getGrade());
        }
        if (src.getPhone() != null) {
            target.setPhone(src.getPhone());
        }
        if (src.getParentWechat() != null) {
            target.setParentWechat(src.getParentWechat());
        }
        if (src.getAddress() != null) {
            target.setAddress(src.getAddress());
        }
        if (src.getPrice() != null) {
            target.setPrice(src.getPrice());
        }
        if (src.getStatus() != null) {
            target.setStatus(src.getStatus());
        }
        if (src.getColor() != null) {
            target.setColor(src.getColor());
        }
        if (src.getRemark() != null) {
            target.setRemark(src.getRemark());
        }
    }

    /** 从调色板中分配一个未被占用（尽量）的颜色。 */
    private String allocateColor() {
        List<Student> all = studentMapper.selectList(null);
        Set<String> used = all.stream()
                .map(Student::getColor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (String color : COLOR_PALETTE) {
            if (!used.contains(color)) {
                return color;
            }
        }
        return COLOR_PALETTE.get(new Random().nextInt(COLOR_PALETTE.size()));
    }
}
