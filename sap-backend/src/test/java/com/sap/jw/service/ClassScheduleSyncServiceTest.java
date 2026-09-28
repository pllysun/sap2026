package com.sap.jw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassScheduleSyncServiceTest {
    ClassScheduleService schedules = mock(ClassScheduleService.class);
    ClassScheduleSyncService sync = new ClassScheduleSyncService(schedules, new ObjectMapper());
    Map<String, Object> first = data("教师甲", "教室1");
    Map<String, Object> second = data("教师乙", "教室2");

    @BeforeEach void setup() {
        when(schedules.terms()).thenReturn(List.of(Map.of("value", "2026-2027-1")));
        when(schedules.scheduleForSync("2026-2027-1", "学院", "专业", "2026", "一班")).thenReturn(first);
        when(schedules.scheduleForSync("2026-2027-1", "学院", "专业", "2026", "二班")).thenReturn(second);
    }
    static Map<String, Object> data(String teacher, String room) {
        return Map.of("term", "2026-2027-1", "semesterStartDate", "2026-09-07", "courses",
            List.of(Map.of("name", "数学", "teacher", teacher, "room", room, "day", 1, "sectionIndex", 1, "weeks", "1-16")));
    }
    ClassScheduleSyncService.Selection selection(String clazz, String revision) {
        return new ClassScheduleSyncService.Selection(clazz, "2026-2027-1", "学院", "2026", "专业", clazz, revision);
    }
    @Test void unchangedReturnsOnlyVersions() {
        var result = sync.sync(new ClassScheduleSyncService.Request(List.of(selection("一班", sync.fingerprint(first))), false));
        assertFalse(result.changed());
        assertNull(result.items().getFirst().data());
    }
    @Test void changingOneReturnsEveryCachedClass() {
        var result = sync.sync(new ClassScheduleSyncService.Request(List.of(selection("一班", null),
            selection("二班", sync.fingerprint(second))), false));
        assertTrue(result.changed());
        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().allMatch(i -> i.data() != null));
    }
    @Test void teacherRoomAndCalendarChangesAreDetected() {
        assertNotEquals(sync.fingerprint(first), sync.fingerprint(data("新教师", "教室1")));
        assertNotEquals(sync.fingerprint(first), sync.fingerprint(data("教师甲", "新教室")));
        var dateChange = new HashMap<>(first); dateChange.put("semesterStartDate", "2026-09-14");
        assertNotEquals(sync.fingerprint(first), sync.fingerprint(dateChange));
    }
    @Test void fingerprintIgnoresRowOrderAndCollectionMetadata() {
        var a = new HashMap<>(first);
        a.put("courses", List.of(Map.of("name", "甲", "day", 1), Map.of("day", 2, "name", "乙")));
        var b = new HashMap<>(a);
        b.put("courses", List.of(Map.of("name", "乙", "day", 2), Map.of("day", 1, "name", "甲")));
        b.put("lastCollectedAt", "new");
        assertEquals(sync.fingerprint(a), sync.fingerprint(b));
    }
    @Test void unavailableTermNeverOverwritesCacheWithEmptySchedule() {
        when(schedules.terms()).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> sync.sync(new ClassScheduleSyncService.Request(List.of(selection("一班", null)), false)));
        verify(schedules, never()).scheduleForSync(any(), any(), any(), any(), any());
    }
    @Test void forceSupportsRefreshingUnchangedChunks() {
        assertNotNull(sync.sync(new ClassScheduleSyncService.Request(List.of(selection("一班", sync.fingerprint(first))), true))
            .items().getFirst().data());
    }
    @Test void rejectsEmptyOversizedDuplicateAndBadDigestBeforeQuery() {
        assertThrows(BusinessException.class, () -> sync.sync(new ClassScheduleSyncService.Request(List.of(), false)));
        assertThrows(BusinessException.class, () -> sync.sync(new ClassScheduleSyncService.Request(Collections.nCopies(101, selection("一班", null)), false)));
        assertThrows(BusinessException.class, () -> sync.sync(new ClassScheduleSyncService.Request(Collections.nCopies(2, selection("一班", null)), false)));
        assertThrows(BusinessException.class, () -> sync.sync(new ClassScheduleSyncService.Request(List.of(selection("一班", "invalid")), false)));
        verify(schedules, never()).terms();
    }
}
