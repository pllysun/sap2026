package com.sap.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.common.Result;
import com.sap.entity.Bill;
import com.sap.service.BillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillControllerTest {

    @Mock BillService billService;

    @InjectMocks BillController controller;

    @Test
    void list_delegatesToService() {
        Page<Map<String, Object>> page = new Page<>(1, 10);
        when(billService.listByGrade("2025", 1, 10)).thenReturn(page);

        Result<?> result = controller.list("2025", 1, 10);

        assertEquals(200, result.getCode());
        assertSame(page, result.getData());
    }

    @Test
    void add_buildsBillWithAllFields() {
        Map<String, Object> params = new HashMap<>();
        params.put("billType", 1);
        params.put("content", "捐款");
        params.put("amount", "100.50");
        params.put("billTime", "2025-01-01 10:00:00");
        params.put("remark", "rmk");
        params.put("grade", "2025");
        params.put("imageUrls", List.of("u1"));

        Result<?> result = controller.add(params);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billService).addBill(captor.capture(), eq(List.of("u1")));
        Bill b = captor.getValue();
        assertEquals(1, b.getBillType());
        assertEquals("捐款", b.getContent());
        assertEquals(new BigDecimal("100.50"), b.getAmount());
        assertEquals(LocalDateTime.of(2025, 1, 1, 10, 0, 0), b.getBillTime());
        assertEquals("2025", b.getGrade());
        assertEquals(200, result.getCode());
    }

    @Test
    void add_nullBillTime_skipsParse() {
        Map<String, Object> params = new HashMap<>();
        params.put("billType", 0);
        params.put("content", "支出");
        params.put("amount", "20");
        params.put("remark", null);
        params.put("grade", null);
        // billTime absent
        // imageUrls absent

        Result<?> result = controller.add(params);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billService).addBill(captor.capture(), any());
        assertNull(captor.getValue().getBillTime());
        assertEquals(200, result.getCode());
    }

    @Test
    void update_buildsBillAndDelegates() {
        Map<String, Object> params = new HashMap<>();
        params.put("billType", 1);
        params.put("content", "c");
        params.put("amount", "5");
        params.put("billTime", "2025-02-02 08:00:00");
        params.put("remark", "r");
        params.put("grade", "2024");
        params.put("imageUrls", List.of("a"));

        Result<?> result = controller.update(9L, params);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billService).updateBill(eq(9L), captor.capture(), eq(List.of("a")));
        assertEquals(LocalDateTime.of(2025, 2, 2, 8, 0, 0), captor.getValue().getBillTime());
        assertEquals(200, result.getCode());
    }

    @Test
    void update_nullBillTime_skipsParse() {
        Map<String, Object> params = new HashMap<>();
        params.put("billType", 0);
        params.put("content", "c");
        params.put("amount", "5");
        params.put("remark", "r");
        params.put("grade", "2024");

        controller.update(9L, params);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billService).updateBill(eq(9L), captor.capture(), any());
        assertNull(captor.getValue().getBillTime());
    }

    @Test
    void delete_delegates() {
        Result<?> result = controller.delete(3L);
        verify(billService).deleteBill(3L);
        assertEquals(200, result.getCode());
    }

    @Test
    void stats_delegates() {
        Map<String, Object> stats = Map.of("count", 1);
        when(billService.getStats("2025")).thenReturn(stats);
        Result<?> result = controller.stats("2025");
        assertSame(stats, result.getData());
    }

    @Test
    void export_returnsResponseEntityWithBytes() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        when(billService.exportExcel("2025")).thenReturn(data);

        ResponseEntity<byte[]> response = controller.export("2025");

        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(data, response.getBody());
        assertNotNull(response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentDisposition().toString().contains("finance_2025.xlsx"));
    }
}
