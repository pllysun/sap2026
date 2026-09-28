package com.sap.jw.service;

import com.sap.jw.client.JwHttpSession;
import com.sap.jw.parser.CalendarParser;
import org.junit.jupiter.api.Test;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwCalendarServiceTest {
    @Test void databaseHitIsSharedWithoutSchoolLogin() {
        AcademicCalendarStore store = mock(AcademicCalendarStore.class);
        JwSessionManager sessions = mock(JwSessionManager.class);
        when(store.find("2026-2027-1")).thenReturn("2026-09-07");
        JwCalendarService service = new JwCalendarService(sessions, new CalendarParser(), store);
        assertEquals("2026-09-07", service.getSemesterStart(1L, "account", "2026-2027-1"));
        assertEquals("2026-09-07", service.getSemesterStart((JwHttpSession) null, "2026-2027-1"));
        verifyNoInteractions(sessions);
    }

    @Test void missingDateFetchedUsingExistingClassSessionAndSaved() throws Exception {
        AcademicCalendarStore store = mock(AcademicCalendarStore.class);
        JwHttpSession session = mock(JwHttpSession.class);
        CalendarParser parser = mock(CalendarParser.class);
        @SuppressWarnings("unchecked") HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(session.getJwglBase()).thenReturn("https://school.test");
        when(session.getFollow(anyString(), eq(6))).thenReturn(response);
        when(response.body()).thenReturn("教学周次".getBytes(StandardCharsets.UTF_8));
        when(parser.parseSemesterStart(anyString(), eq("2025-2026-2"))).thenReturn("2026-03-09");
        JwCalendarService service = new JwCalendarService(mock(JwSessionManager.class), parser, store);
        assertEquals("2026-03-09", service.getSemesterStart(session, "2025-2026-2"));
        verify(store).save("2025-2026-2", "2026-03-09", false, null);
        verify(session).getFollow("https://school.test/jsxsd/jxzl/jxzl_query?xnxq01id=2025-2026-2", 6);
    }
}
