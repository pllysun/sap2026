package com.sap.controller;

import com.sap.common.Result;
import com.sap.entity.Position;
import com.sap.service.PositionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PositionControllerTest {

    @Mock PositionService positionService;

    @InjectMocks PositionController controller;

    @Test
    void list_delegates() {
        List<Position> data = List.of(new Position());
        when(positionService.listAll()).thenReturn(data);
        Result<?> result = controller.list();
        assertSame(data, result.getData());
    }

    @Test
    void add_delegates() {
        Position p = new Position();
        Result<?> result = controller.add(p);
        verify(positionService).addPosition(p);
        assertEquals(200, result.getCode());
    }

    @Test
    void update_delegates() {
        Position p = new Position();
        Result<?> result = controller.update(5, p);
        verify(positionService).updatePosition(5, p);
        assertEquals(200, result.getCode());
    }

    @Test
    void delete_delegates() {
        Result<?> result = controller.delete(8);
        verify(positionService).deletePosition(8);
        assertEquals(200, result.getCode());
    }
}
