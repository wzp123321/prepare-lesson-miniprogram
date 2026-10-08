package com.lesson.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lesson.schedule.entity.Student;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 学生 Mapper（S01）。基础 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供，
 * 此处补充「按 keyword/grade/status 条件查询」等常用查询（A-12）。
 */
public interface StudentMapper extends BaseMapper<Student> {

    /**
     * 列表 + 搜索：keyword 匹配 姓名/电话，grade 精确，status 精确。
     */
    List<Student> listByCondition(@Param("keyword") String keyword,
                                  @Param("grade") String grade,
                                  @Param("status") Integer status);
}
