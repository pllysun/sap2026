package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.entity.Setting;
import com.sap.mapper.SettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingServiceTest extends BaseUnitTest {

    @Mock SettingMapper settingMapper;

    @InjectMocks SettingService service;

    private Setting setting(String key, String value, String desc) {
        Setting s = new Setting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        s.setDescription(desc);
        return s;
    }

    // ===================== initDefaultSettings / insertIfAbsent =====================

    @Test
    void initDefaultSettings_allAbsent_insertsEachDefault() {
        when(settingMapper.selectOne(any())).thenReturn(null);

        service.initDefaultSettings();

        // 27 default keys are inserted when none exist
        verify(settingMapper, times(27)).insert(any(Setting.class));
        verify(settingMapper, never()).updateById(any());
    }

    @Test
    void initDefaultSettings_existingWithDescription_noInsertNoUpdate() {
        when(settingMapper.selectOne(any()))
                .thenReturn(setting("footer_address", "x", "已有描述"));

        service.initDefaultSettings();

        verify(settingMapper, never()).insert(any());
        verify(settingMapper, never()).updateById(any());
    }

    @Test
    void initDefaultSettings_existingBlankDescription_backfillsDescription() {
        // existing rows present but with blank description -> update path.
        // Return a fresh instance per call so mutation in one iteration does not leak.
        when(settingMapper.selectOne(any()))
                .thenAnswer(inv -> setting("footer_address", "x", "   "));

        service.initDefaultSettings();

        verify(settingMapper, never()).insert(any());
        verify(settingMapper, times(27)).updateById(any(Setting.class));
    }

    @Test
    void initDefaultSettings_existingNullDescription_backfillsDescription() {
        when(settingMapper.selectOne(any()))
                .thenAnswer(inv -> setting("footer_address", "x", null));

        service.initDefaultSettings();

        verify(settingMapper, never()).insert(any());
        verify(settingMapper, times(27)).updateById(any(Setting.class));
    }

    // ===================== getValue =====================

    @Test
    void getValue_found_returnsValue() {
        when(settingMapper.selectOne(any())).thenReturn(setting("k", "v", "d"));
        assertEquals("v", service.getValue("k"));
    }

    @Test
    void getValue_notFound_returnsNull() {
        when(settingMapper.selectOne(any())).thenReturn(null);
        assertNull(service.getValue("missing"));
    }

    // ===================== updateSetting =====================

    @Test
    void updateSetting_existing_updatesValueAndDescription() {
        Setting existing = setting("k", "old", "oldDesc");
        when(settingMapper.selectOne(any())).thenReturn(existing);

        service.updateSetting(setting("k", "new", "newDesc"));

        assertEquals("new", existing.getSettingValue());
        assertEquals("newDesc", existing.getDescription());
        verify(settingMapper).updateById(existing);
        verify(settingMapper, never()).insert(any());
    }

    @Test
    void updateSetting_existing_nullDescriptionKeepsOld() {
        Setting existing = setting("k", "old", "oldDesc");
        when(settingMapper.selectOne(any())).thenReturn(existing);

        Setting incoming = setting("k", "new", null);
        service.updateSetting(incoming);

        assertEquals("new", existing.getSettingValue());
        assertEquals("oldDesc", existing.getDescription());
        verify(settingMapper).updateById(existing);
    }

    @Test
    void updateSetting_notExisting_inserts() {
        when(settingMapper.selectOne(any())).thenReturn(null);
        Setting incoming = setting("newKey", "v", "d");

        service.updateSetting(incoming);

        verify(settingMapper).insert(incoming);
        verify(settingMapper, never()).updateById(any());
    }

    // ===================== getCurrentGrade =====================

    @Test
    void getCurrentGrade_delegatesToGetValue() {
        when(settingMapper.selectOne(any())).thenReturn(setting("current_grade", "2026", "d"));
        assertEquals("2026", service.getCurrentGrade());
    }

    // ===================== getPublicSettings =====================

    @Test
    void getPublicSettings_mapsKeyToValue() {
        List<Setting> list = List.of(
                setting("footer_qq", "111", "d"),
                setting("qr_qq_group_url", "u", "d"),
                setting("join_group_link", "link", "d"));
        when(settingMapper.selectList(any())).thenReturn(list);

        Map<String, String> map = service.getPublicSettings();

        assertEquals(3, map.size());
        assertEquals("111", map.get("footer_qq"));
        assertEquals("u", map.get("qr_qq_group_url"));
        assertEquals("link", map.get("join_group_link"));
    }

    @Test
    void getPublicSettings_empty_returnsEmptyMap() {
        when(settingMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.getPublicSettings().isEmpty());
    }
}
