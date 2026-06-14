package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.BaseUnitTest;
import com.sap.entity.StudyMaterial;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.StudyActivityMapper;
import com.sap.mapper.StudyLeaderMapper;
import com.sap.mapper.StudyMaterialMapper;
import com.sap.mapper.StudyMemberMapper;
import com.sap.mapper.StudyScoreMapper;
import com.sap.mapper.TermMapper;
import com.sap.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 覆盖作业管理：uploadHomework / deleteHomework / deleteMySubmission / submitStudentHomework。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyServiceHomeworkTest extends BaseUnitTest {

    @Mock StudyActivityMapper studyActivityMapper;
    @Mock StudyLeaderMapper studyLeaderMapper;
    @Mock StudyMemberMapper studyMemberMapper;
    @Mock StudyScoreMapper studyScoreMapper;
    @Mock StudyMaterialMapper studyMaterialMapper;
    @Mock TermMapper termMapper;
    @Mock UserMapper userMapper;
    @Mock SettingMapper settingMapper;
    @Mock PositionMapper positionMapper;
    @Mock CacheService cacheService;

    @InjectMocks StudyService service;

    // ===================== uploadHomework =====================

    @Test
    void uploadHomework_deletesOldThenInserts_usesTitle() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginId).thenReturn(7L);

            service.uploadHomework(1L, 2, "第二周作业", "url", "hw.pdf");

            verify(studyMaterialMapper).delete(any());
            ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
            verify(studyMaterialMapper).insert(captor.capture());
            StudyMaterial m = captor.getValue();
            assertEquals(1L, m.getActivityId());
            assertEquals(2, m.getWeek());
            assertEquals(1, m.getFileType());
            assertEquals("第二周作业", m.getTitle());
            assertEquals("url", m.getFileUrl());
            assertEquals("hw.pdf", m.getFileName());
            assertEquals(7L, m.getUserId());
        }
    }

    @Test
    void uploadHomework_nullTitle_fallsBackToFileName() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginId).thenReturn(7L);

            service.uploadHomework(1L, 2, null, "url", "fallback.pdf");

            ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
            verify(studyMaterialMapper).insert(captor.capture());
            assertEquals("fallback.pdf", captor.getValue().getTitle());
        }
    }

    // ===================== deleteHomework =====================

    @Test
    void deleteHomework_deletesFileType1() {
        service.deleteHomework(1L, 3);
        verify(studyMaterialMapper).delete(any());
    }

    // ===================== deleteMySubmission =====================

    @Test
    void deleteMySubmission_deletesFileType2ForUser() {
        service.deleteMySubmission(1L, 3, 50L);
        verify(studyMaterialMapper).delete(any());
    }

    // ===================== submitStudentHomework =====================

    @Test
    void submitStudentHomework_deletesOldThenInserts_usesTitle() {
        service.submitStudentHomework(1L, 2, 50L, "我的作业", "url", "my.pdf");

        verify(studyMaterialMapper).delete(any());
        ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
        verify(studyMaterialMapper).insert(captor.capture());
        StudyMaterial m = captor.getValue();
        assertEquals(2, m.getFileType());
        assertEquals(50L, m.getUserId());
        assertEquals("我的作业", m.getTitle());
        assertEquals("my.pdf", m.getFileName());
    }

    @Test
    void submitStudentHomework_nullTitle_fallsBackToFileName() {
        service.submitStudentHomework(1L, 2, 50L, null, "url", "auto.pdf");

        ArgumentCaptor<StudyMaterial> captor = ArgumentCaptor.forClass(StudyMaterial.class);
        verify(studyMaterialMapper).insert(captor.capture());
        assertEquals("auto.pdf", captor.getValue().getTitle());
    }
}
