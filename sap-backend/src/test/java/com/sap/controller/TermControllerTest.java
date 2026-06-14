package com.sap.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.common.Result;
import com.sap.entity.Term;
import com.sap.service.TermService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TermControllerTest {

    @Mock TermService termService;

    @InjectMocks TermController controller;

    @Test
    void list_delegatesToService() {
        Page<Map<String, Object>> page = new Page<>(1, 40);
        when(termService.listByGradePaged("2025", 1, 40)).thenReturn(page);

        Result<?> result = controller.list("2025", 1, 40);

        assertEquals(200, result.getCode());
        assertSame(page, result.getData());
    }

    @Test
    void grades_delegatesToService() {
        when(termService.listGrades()).thenReturn(List.of("2025", "2024"));
        Result<?> result = controller.grades();
        assertEquals(List.of("2025", "2024"), result.getData());
    }

    @Test
    void add_delegatesToService() {
        Term term = new Term();
        Result<?> result = controller.add(term);
        verify(termService).addTerm(term);
        assertEquals(200, result.getCode());
    }

    @Test
    void delete_delegatesToService() {
        Result<?> result = controller.delete(7L);
        verify(termService).deleteTerm(7L);
        assertEquals(200, result.getCode());
    }

    @Test
    void changeover_delegatesToService() {
        List<Map<String, Object>> assignments = List.of(Map.of("positionId", 1));
        Result<?> result = controller.changeover(assignments);
        verify(termService).changeover(assignments);
        assertEquals(200, result.getCode());
    }
}
