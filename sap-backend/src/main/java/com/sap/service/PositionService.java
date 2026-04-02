package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.Position;
import com.sap.mapper.PositionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PositionService {

    @Autowired
    private PositionMapper positionMapper;

    public List<Position> listAll() {
        return positionMapper.selectList(
                new LambdaQueryWrapper<Position>().orderByAsc(Position::getSortOrder)
        );
    }

    public void addPosition(Position position) {
        position.setIsSystem(0);
        positionMapper.insert(position);
    }

    public void updatePosition(Integer id, Position position) {
        Position existing = positionMapper.selectById(id);
        if (existing == null) throw new BusinessException("身份不存在");
        if (existing.getIsSystem() == 1) throw new BusinessException("系统内置身份不可修改");
        existing.setPositionName(position.getPositionName());
        existing.setSortOrder(position.getSortOrder());
        positionMapper.updateById(existing);
    }

    public void deletePosition(Integer id) {
        Position existing = positionMapper.selectById(id);
        if (existing == null) throw new BusinessException("身份不存在");
        if (existing.getIsSystem() == 1) throw new BusinessException("系统内置身份不可删除");
        positionMapper.deleteById(id);
    }
}
