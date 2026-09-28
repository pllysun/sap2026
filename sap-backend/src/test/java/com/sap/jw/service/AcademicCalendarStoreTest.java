package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.entity.Setting;
import com.sap.mapper.SettingMapper;
import com.sap.service.SettingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcademicCalendarStoreTest {
    private final SettingMapper mapper = mock(SettingMapper.class);
    private final SettingService settings = mock(SettingService.class);
    private final AcademicCalendarStore store = new AcademicCalendarStore(mapper, settings);

    @Test void readsExistingGlobalDatesWithoutMigration() {
        when(settings.getValue("term_start_2026-2027-1")).thenReturn("2026-09-07");
        assertEquals("2026-09-07", store.find("2026-2027-1"));
    }

    @Test void scrapeOnlyFillsMissingAndManualEditCanReplace() {
        when(settings.getValue("term_start_2026-2027-1")).thenReturn("2026-09-07");
        store.save("2026-2027-1", "2026-09-14", false, null);
        verify(settings, never()).updateSetting(any());
        store.save("2026-2027-1", "2026-09-14", true, 12L);
        ArgumentCaptor<Setting> row = ArgumentCaptor.forClass(Setting.class);
        verify(settings).updateSetting(row.capture());
        assertEquals("term_start_2026-2027-1", row.getValue().getSettingKey());
        assertEquals("2026-09-14", row.getValue().getSettingValue());
        assertTrue(row.getValue().getDescription().contains("12"));
    }

    @Test void missingDateIsSavedAndMalformedDatesAreRejected() {
        store.save("2025-2026-2", "2026-03-09", false, null);
        verify(settings).updateSetting(any());
        assertThrows(BusinessException.class, () -> store.save("2026-2028-1", "2026-09-07", true, 1L));
        assertThrows(BusinessException.class, () -> store.save("2025-2026-2", "2025-03-09", true, 1L));
        assertThrows(BusinessException.class, () -> store.save("2025-2026-2", "2026-02-30", true, 1L));
        assertThrows(BusinessException.class, () -> store.delete("app_version_code"));
    }

    @Test void listNeverExposesUnrelatedSettings() {
        Setting good = new Setting(); good.setSettingKey("term_start_2025-2026-2"); good.setSettingValue("2026-03-09");
        Setting bad = new Setting(); bad.setSettingKey("termXstartXsecret"); bad.setSettingValue("private");
        when(mapper.selectList(any())).thenReturn(List.of(good, bad));
        assertEquals(1, store.list().size());
        assertEquals("2025-2026-2", store.list().get(0).term());
    }
}
