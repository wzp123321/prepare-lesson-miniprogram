package com.lesson.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lesson.schedule.entity.Dict;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 字典 Mapper（S07）。基础 CRUD 由 MyBatis-Plus 提供，补充「按 dict_type 列表」（A-16）。
 */
public interface DictMapper extends BaseMapper<Dict> {

    /** 按字典类型返回字典项（如 grade / absent_reason）。 */
    List<Dict> listByType(@Param("dictType") String dictType);
}
