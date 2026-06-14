package com.sap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.Bill;
import com.sap.entity.BillImage;
import com.sap.entity.Setting;
import com.sap.mapper.BillImageMapper;
import com.sap.mapper.BillMapper;
import com.sap.mapper.SettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillServiceTest extends BaseUnitTest {

    @Mock BillMapper billMapper;
    @Mock BillImageMapper billImageMapper;
    @Mock SettingMapper settingMapper;

    @InjectMocks BillService service;

    private Bill bill(long id, int type, String amount, String grade) {
        Bill b = new Bill();
        b.setId(id);
        b.setBillType(type);
        b.setContent("c" + id);
        b.setAmount(new BigDecimal(amount));
        b.setBillTime(LocalDateTime.of(2025, 1, 1, 10, 0, 0));
        b.setRemark("r" + id);
        b.setGrade(grade);
        return b;
    }

    // ---------- listByGrade ----------
    @Test
    void listByGrade_withGrade_populatesRecordsAndImages() {
        when(billMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Bill> p = inv.getArgument(0);
            p.setRecords(List.of(bill(1, 1, "100", "2025")));
            p.setTotal(1);
            return p;
        });
        BillImage img = new BillImage();
        img.setImageUrl("u");
        when(billImageMapper.selectList(any())).thenReturn(List.of(img));

        Page<Map<String, Object>> result = service.listByGrade("2025", 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        Map<String, Object> rec = result.getRecords().get(0);
        assertEquals(1L, rec.get("id"));
        assertEquals(1, rec.get("billType"));
        assertEquals("2025", rec.get("grade"));
        assertEquals(List.of(img), rec.get("images"));
    }

    @Test
    void listByGrade_nullGrade_doesNotFilter() {
        when(billMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Bill> p = inv.getArgument(0);
            p.setRecords(List.of());
            p.setTotal(0);
            return p;
        });

        Page<Map<String, Object>> result = service.listByGrade(null, 1, 10);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void listByGrade_emptyGrade_doesNotFilter() {
        when(billMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Bill> p = inv.getArgument(0);
            p.setRecords(List.of(bill(2, 0, "50", "2024")));
            p.setTotal(1);
            return p;
        });
        when(billImageMapper.selectList(any())).thenReturn(List.of());

        Page<Map<String, Object>> result = service.listByGrade("", 1, 10);

        assertEquals(1, result.getRecords().size());
    }

    // ---------- addBill ----------
    @Test
    void addBill_withGrade_insertsWithImages() {
        Bill b = bill(0, 1, "100", "2025");
        service.addBill(b, List.of("a", "b"));

        verify(settingMapper, never()).selectOne(any());
        verify(billMapper).insert(b);
        verify(billImageMapper, times(2)).insert(any(BillImage.class));
    }

    @Test
    void addBill_blankGrade_usesSettingValue() {
        Bill b = bill(0, 1, "100", "");
        Setting s = new Setting();
        s.setSettingValue("2030");
        when(settingMapper.selectOne(any())).thenReturn(s);

        service.addBill(b, null);

        assertEquals("2030", b.getGrade());
        verify(billMapper).insert(b);
        verify(billImageMapper, never()).insert(any());
    }

    @Test
    void addBill_nullGradeAndNoSetting_defaultsTo2025() {
        Bill b = bill(0, 1, "100", null);
        when(settingMapper.selectOne(any())).thenReturn(null);

        service.addBill(b, List.of());

        assertEquals("2025", b.getGrade());
        verify(billImageMapper, never()).insert(any());
    }

    // ---------- updateBill ----------
    @Test
    void updateBill_updatesFieldsAndReplacesImages() {
        when(billMapper.selectById(5L)).thenReturn(bill(5, 0, "10", "2024"));
        Bill changed = bill(0, 1, "999", "2025");

        service.updateBill(5L, changed, List.of("x"));

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper).updateById(captor.capture());
        Bill saved = captor.getValue();
        assertEquals(1, saved.getBillType());
        assertEquals(new BigDecimal("999"), saved.getAmount());
        assertEquals("2025", saved.getGrade());
        verify(billImageMapper).delete(any());
        verify(billImageMapper).insert(any(BillImage.class));
    }

    @Test
    void updateBill_nullImages_keepsImagesUntouched() {
        when(billMapper.selectById(5L)).thenReturn(bill(5, 0, "10", "2024"));
        service.updateBill(5L, bill(0, 1, "1", "2025"), null);
        verify(billImageMapper, never()).delete(any());
        verify(billImageMapper, never()).insert(any());
    }

    @Test
    void updateBill_notFound_throws() {
        when(billMapper.selectById(9L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateBill(9L, new Bill(), null));
        assertEquals("账单不存在", ex.getMessage());
    }

    // ---------- deleteBill ----------
    @Test
    void deleteBill_cascadesImages() {
        service.deleteBill(3L);
        verify(billMapper).deleteById(3L);
        verify(billImageMapper).delete(any());
    }

    // ---------- getStats ----------
    @Test
    void getStats_computesIncomeExpenseAndBalance() {
        when(billMapper.selectList(any())).thenReturn(List.of(
                bill(1, 1, "100", "2025"),
                bill(2, 1, "50", "2025"),
                bill(3, 0, "30", "2025")
        ));

        Map<String, Object> stats = service.getStats("2025");

        assertEquals(new BigDecimal("150"), stats.get("totalIncome"));
        assertEquals(new BigDecimal("30"), stats.get("totalExpense"));
        assertEquals(new BigDecimal("120"), stats.get("balance"));
        assertEquals(3, stats.get("count"));
    }

    @Test
    void getStats_nullGrade_emptyList_returnsZeros() {
        when(billMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> stats = service.getStats(null);

        assertEquals(BigDecimal.ZERO, stats.get("totalIncome"));
        assertEquals(BigDecimal.ZERO, stats.get("totalExpense"));
        assertEquals(BigDecimal.ZERO, stats.get("balance"));
        assertEquals(0, stats.get("count"));
    }

    @Test
    void getStats_emptyGrade_doesNotFilter() {
        when(billMapper.selectList(any())).thenReturn(List.of(bill(1, 1, "5", "2025")));
        Map<String, Object> stats = service.getStats("");
        assertEquals(new BigDecimal("5"), stats.get("totalIncome"));
    }

    // ---------- exportExcel ----------
    @Test
    void exportExcel_returnsNonEmptyBytes_withGradeAndRows() throws Exception {
        Bill income = bill(1, 1, "100.5", "2025");
        Bill expenseWithNulls = new Bill();
        expenseWithNulls.setId(2L);
        expenseWithNulls.setBillType(0);
        expenseWithNulls.setContent("only-content");
        // amount/billTime/remark/grade left null to cover ternary fallbacks
        when(billMapper.selectList(any())).thenReturn(List.of(income, expenseWithNulls));

        byte[] data = service.exportExcel("2025");

        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    void exportExcel_nullGrade_emptyList() throws Exception {
        when(billMapper.selectList(any())).thenReturn(List.of());
        byte[] data = service.exportExcel(null);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }
}
