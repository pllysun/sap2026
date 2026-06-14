package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Position;
import com.sap.mapper.PositionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PositionServiceTest extends BaseUnitTest {

    @Mock PositionMapper positionMapper;

    @InjectMocks PositionService service;

    private Position pos(int id, String name, int isSystem) {
        Position p = new Position();
        p.setId(id);
        p.setPositionName(name);
        p.setIsSystem(isSystem);
        p.setSortOrder(1);
        p.setMaxCount(1);
        return p;
    }

    @Test
    void listAll_delegatesToMapper() {
        List<Position> data = List.of(pos(1, "会长", 1));
        when(positionMapper.selectList(any())).thenReturn(data);
        assertEquals(data, service.listAll());
    }

    @Test
    void addPosition_forcesIsSystemZeroAndInserts() {
        Position p = pos(0, "自定义", 1); // isSystem will be overwritten
        service.addPosition(p);
        assertEquals(0, p.getIsSystem());
        verify(positionMapper).insert(p);
    }

    @Test
    void updatePosition_customRole_updates() {
        when(positionMapper.selectById(5)).thenReturn(pos(5, "旧名", 0));
        Position changed = pos(0, "新名", 0);
        changed.setSortOrder(9);

        service.updatePosition(5, changed);

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionMapper).updateById(captor.capture());
        assertEquals("新名", captor.getValue().getPositionName());
        assertEquals(9, captor.getValue().getSortOrder());
    }

    @Test
    void updatePosition_notFound_throws() {
        when(positionMapper.selectById(99)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePosition(99, new Position()));
        assertEquals("身份不存在", ex.getMessage());
    }

    @Test
    void updatePosition_systemBuiltIn_throws() {
        when(positionMapper.selectById(1)).thenReturn(pos(1, "会长", 1));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePosition(1, new Position()));
        assertEquals("系统内置身份不可修改", ex.getMessage());
        verify(positionMapper, never()).updateById(any());
    }

    @Test
    void deletePosition_customRole_deletes() {
        when(positionMapper.selectById(5)).thenReturn(pos(5, "自定义", 0));
        service.deletePosition(5);
        verify(positionMapper).deleteById(5);
    }

    @Test
    void deletePosition_notFound_throws() {
        when(positionMapper.selectById(99)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deletePosition(99));
        assertEquals("身份不存在", ex.getMessage());
    }

    @Test
    void deletePosition_systemBuiltIn_throws() {
        when(positionMapper.selectById(1)).thenReturn(pos(1, "会长", 1));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deletePosition(1));
        assertEquals("系统内置身份不可删除", ex.getMessage());
        verify(positionMapper, never()).deleteById(anyInt());
    }
}
