package com.sap.jw.service;

import com.sap.jw.client.JwHttpSession;
import com.sap.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClassSchedulePagingTest {
    final ClassScheduleService service = new ClassScheduleService(mock(JdbcTemplate.class), mock(JwSessionManager.class),
            mock(JwCredentialService.class), mock(UserMapper.class), mock(JwCalendarService.class), mock(PlatformTransactionManager.class));

    JwHttpSession session(List<String> pages, List<ClassScheduleService.PageProgress> progress) throws Exception {
        var session = mock(JwHttpSession.class);
        when(session.getJwglBase()).thenReturn("https://school.test");
        var cursor = new AtomicInteger();
        when(session.getFollow(anyString(), eq(4), anyMap())).thenAnswer(call -> {
            int page = cursor.getAndIncrement();
            assertTrue(call.getArgument(0, String.class).contains("pageNum=" + (page + 1)));
            assertEquals(page, progress.size(), "上一页应在请求下一页前完成解析并上报");
            ReflectionTestUtils.setField(service, "lastRequestAt", 0L);
            var response = mock(HttpResponse.class);
            when(response.body()).thenReturn(pages.get(page).getBytes(StandardCharsets.UTF_8));
            return response;
        });
        return session;
    }

    @Test void reportsEachPageUsingActualPageSizeAndSeparatesRawAndUniqueRows() throws Exception {
        var progress = new ArrayList<ClassScheduleService.PageProgress>();
        var session = session(List.of(
                "{\"code\":0,\"count\":5,\"data\":[{\"kcmc\":\"A\"},{\"kcmc\":\"B\"}]}",
                "{\"code\":0,\"count\":5,\"data\":[{\"kcmc\":\"B\"},{\"kcmc\":\"C\"}]}",
                "{\"code\":0,\"count\":5,\"data\":[{\"kcmc\":\"D\"}]}"), progress);
        var result = service.fetchAll(session, "/data", "/page", Map.of(), "2026-2027-1", progress::add);
        assertEquals(List.of(1, 2, 3), progress.stream().map(ClassScheduleService.PageProgress::completedPages).toList());
        assertTrue(progress.stream().allMatch(p -> p.totalPages() == 3));
        assertEquals(5, result.fetchedCount());
        assertEquals(4, result.rows().size());
        assertEquals(3, result.pages());
    }

    @Test void missingTotalStaysUnknownUntilExplicitEmptyPage() throws Exception {
        var progress = new ArrayList<ClassScheduleService.PageProgress>();
        var session = session(List.of("{\"code\":0,\"data\":[{\"kcmc\":\"A\"}]}", "{\"code\":0,\"data\":[]}"), progress);
        var result = service.fetchAll(session, "/data", "/page", Map.of(), "term", progress::add);
        assertEquals(-1, progress.getFirst().totalPages());
        assertEquals(1, progress.getLast().totalPages());
        assertEquals(1, result.pages());
    }

    @Test void truncatedPagingFailsInsteadOfPublishingPartialData() throws Exception {
        var progress = new ArrayList<ClassScheduleService.PageProgress>();
        var session = session(List.of("{\"code\":0,\"count\":4,\"data\":[{\"kcmc\":\"A\"},{\"kcmc\":\"B\"}]}", "{\"code\":0,\"count\":4,\"data\":[]}"), progress);
        assertThrows(IllegalStateException.class, () -> service.fetchAll(session, "/data", "/page", Map.of(), "term", progress::add));
        assertEquals(1, progress.size());
    }
}
