package com.lesson.schedule.service.impl;

import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.entity.Dict;
import com.lesson.schedule.mapper.DictMapper;
import com.lesson.schedule.service.DictService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictMapper dictMapper;

    @Override
    public List<Dict> listByType(String type) {
        return dictMapper.listByType(type);
    }

    @Override
    public Long createDict(Dict dict) {
        if (dict.getDictType() == null || dict.getDictType().isBlank()) {
            throw new BusinessException(400, "字典类型不能为空");
        }
        if (dict.getEnabled() == null) {
            dict.setEnabled(1);
        }
        dict.setId(null);
        dictMapper.insert(dict);
        return dict.getId();
    }

    @Override
    public void updateDict(Long id, Dict dict) {
        Dict existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "字典项不存在");
        }
        if (dict.getDictType() != null) {
            existing.setDictType(dict.getDictType());
        }
        if (dict.getDictValue() != null) {
            existing.setDictValue(dict.getDictValue());
        }
        if (dict.getSortOrder() != null) {
            existing.setSortOrder(dict.getSortOrder());
        }
        if (dict.getEnabled() != null) {
            existing.setEnabled(dict.getEnabled());
        }
        dictMapper.updateById(existing);
    }

    @Override
    public void deleteDict(Long id) {
        Dict existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "字典项不存在");
        }
        dictMapper.deleteById(id);
    }
}
