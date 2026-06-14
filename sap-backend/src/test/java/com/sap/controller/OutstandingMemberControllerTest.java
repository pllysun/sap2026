package com.sap.controller;

import com.sap.common.Result;
import com.sap.entity.OutstandingMember;
import com.sap.service.OutstandingMemberService;
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
class OutstandingMemberControllerTest {

    @Mock OutstandingMemberService service;

    @InjectMocks OutstandingMemberController controller;

    @Test
    void listAll_delegates() {
        List<OutstandingMember> data = List.of(new OutstandingMember());
        when(service.listAll()).thenReturn(data);
        Result<?> result = controller.listAll();
        assertSame(data, result.getData());
    }

    @Test
    void list_delegates() {
        List<OutstandingMember> data = List.of(new OutstandingMember());
        when(service.listByGrade("2025")).thenReturn(data);
        Result<?> result = controller.list("2025");
        assertSame(data, result.getData());
    }

    @Test
    void add_delegates() {
        OutstandingMember m = new OutstandingMember();
        Result<?> result = controller.add(m);
        verify(service).add(m);
        assertEquals(200, result.getCode());
    }

    @Test
    void update_delegates() {
        OutstandingMember m = new OutstandingMember();
        Result<?> result = controller.update(5L, m);
        verify(service).update(5L, m);
        assertEquals(200, result.getCode());
    }

    @Test
    void delete_delegates() {
        Result<?> result = controller.delete(8L);
        verify(service).delete(8L);
        assertEquals(200, result.getCode());
    }
}
