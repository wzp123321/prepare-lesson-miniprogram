package com.lesson.schedule.service;

import com.lesson.schedule.entity.Dict;
import java.util.List;

/**
 * 字典服务（S07）。年级 / 顺延原因两类维护，增删查。
 */
public interface DictService {

    /** BE-API-26 列表（按 dict_type）。 */
    List<Dict> listByType(String type);

    /** BE-API-27 新增。返回生成主键。 */
    Long createDict(Dict dict);

    /** BE-API-28 修改。 */
    void updateDict(Long id, Dict dict);

    /** BE-API-29 删除。 */
    void deleteDict(Long id);
}
