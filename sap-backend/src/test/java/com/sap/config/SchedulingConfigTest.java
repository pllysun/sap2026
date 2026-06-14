package com.sap.config;

import com.sap.service.StudyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * 定时任务：作业定时发布触发器。
 */
@ExtendWith(MockitoExtension.class)
class SchedulingConfigTest {

    @Mock StudyService studyService;
    @InjectMocks SchedulingConfig schedulingConfig;

    @Test
    void publishScheduledHomework_advanced_callsServiceAndLogs() {
        when(studyService.processScheduledPublish()).thenReturn(2);
        schedulingConfig.publishScheduledHomework();
        verify(studyService).processScheduledPublish();
    }

    @Test
    void publishScheduledHomework_noAdvance_callsService() {
        when(studyService.processScheduledPublish()).thenReturn(0);
        schedulingConfig.publishScheduledHomework();
        verify(studyService).processScheduledPublish();
    }

    @Test
    void publishScheduledHomework_serviceThrows_swallowed() {
        when(studyService.processScheduledPublish()).thenThrow(new RuntimeException("boom"));
        assertDoesNotThrow(() -> schedulingConfig.publishScheduledHomework());
    }
}
