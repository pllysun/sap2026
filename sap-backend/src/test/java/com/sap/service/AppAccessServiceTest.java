package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.entity.Setting;
import com.sap.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppAccessServiceTest {

    @Mock SettingService settingService;
    @Mock UserRoleMapper userRoleMapper;

    @Test
    void basicSchedulesStayAvailableAtEveryGuestCloudLevel() {
        AppAccessService service = new AppAccessService(settingService, userRoleMapper);
        when(userRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of(4));
        for (int level = 0; level <= 2; level++) {
            when(settingService.getValue(AppAccessService.GUEST_ACCESS_LEVEL_KEY)).thenReturn(String.valueOf(level));
            assertTrue(service.hasBasicAccess(2L));
            service.requireBasicAccess(2L);
            assertEquals(level == 2, service.hasFullAccess(2L));
        }
    }

    @Test
    void guestLevelIsClampedAndMalformedValueClosesAccess() {
        AppAccessService service = new AppAccessService(settingService, userRoleMapper);
        when(settingService.getValue(AppAccessService.GUEST_ACCESS_LEVEL_KEY))
                .thenReturn("2", "99", "bad");

        assertEquals(2, service.guestAccessLevel());
        assertEquals(2, service.guestAccessLevel());
        assertEquals(0, service.guestAccessLevel());
    }

    @Test
    void realMemberAlwaysHasFullAccessWhileGuestFollowsCloudLevel() {
        AppAccessService service = new AppAccessService(settingService, userRoleMapper);
        when(userRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of(3));
        when(userRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of(4));
        when(settingService.getValue(AppAccessService.GUEST_ACCESS_LEVEL_KEY)).thenReturn("1");

        assertEquals(2, service.effectiveLevel(1L));
        assertEquals(1, service.effectiveLevel(2L));
        assertTrue(service.hasBasicAccess(2L));
        assertFalse(service.hasFullAccess(2L));
        assertThrows(BusinessException.class, () -> service.requireFullAccess(2L));
    }

    @Test
    void updatePersistsOnlyValidThreeLevelValue() {
        AppAccessService service = new AppAccessService(settingService, userRoleMapper);
        service.updateGuestAccessLevel(2);

        ArgumentCaptor<Setting> captor = ArgumentCaptor.forClass(Setting.class);
        verify(settingService).updateSetting(captor.capture());
        assertEquals(AppAccessService.GUEST_ACCESS_LEVEL_KEY, captor.getValue().getSettingKey());
        assertEquals("2", captor.getValue().getSettingValue());
        assertThrows(BusinessException.class, () -> service.updateGuestAccessLevel(3));
    }
}
