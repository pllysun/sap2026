package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.JwAuthClient;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.client.MfaRequiredException;
import com.sap.jw.client.PendingCas;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClassScheduleTaskServiceTest {
    final ClassScheduleService schedules = mock(ClassScheduleService.class);
    final PendingClassScheduleManager pending = new PendingClassScheduleManager();
    final JwAuthClient auth = mock(JwAuthClient.class);
    final JwSessionManager sessions = mock(JwSessionManager.class);
    final ArrayDeque<Runnable> work = new ArrayDeque<>();
    final ClassScheduleTaskService tasks = new ClassScheduleTaskService(schedules, pending, auth, sessions, work::add);
    final String batch = UUID.randomUUID().toString();

    @Test void startReturnsBatchBeforeNetworkAndRejectsConcurrentCollection() {
        var accepted = tasks.start(10L, 10L, "test", "password", "term", batch);
        assertEquals(batch, accepted.get("batchId"));
        verify(schedules, never()).pullWithCredentials(any(), any(), any(), any(), any(), any(), any());
        assertThrows(BusinessException.class, () -> tasks.start(10L, 10L, "test", "password", "term", null));
        tasks.start(10L, 10L, "test", "password", "term", batch);
        assertEquals(1, work.size(), "同批次重试不应重复采集");
        work.remove().run();
        verify(schedules).pullWithCredentials(10L, "test", "password", "term", "MANUAL", null, batch);
        assertNull(tasks.activeBatchId());
    }

    @Test void smsWaitCanResumeSameBatchAndChallengeIsOnlyVisibleToInitiator() {
        var cas = mock(PendingCas.class);
        when(schedules.pullWithCredentials(any(), any(), any(), any(), any(), any(), any())).thenThrow(new MfaRequiredException(cas, "138****0000"));
        when(schedules.progress(batch)).thenAnswer(call -> new HashMap<>(Map.of("status", "PENDING")));
        tasks.start(10L, 10L, "test", "password", "term", batch);
        work.remove().run();
        assertEquals(batch, tasks.activeBatchId());
        assertFalse(tasks.progress(batch, 20L).containsKey("challengeId"));
        String challenge = (String) tasks.progress(batch, 10L).get("challengeId");
        assertNotNull(challenge);
        assertThrows(BusinessException.class, () -> tasks.resume(20L, challenge, "123456"));
        var session = mock(JwHttpSession.class);
        when(auth.continueWithMfa(cas, "123456")).thenReturn(session);
        tasks.resume(10L, challenge, "123456");
        verifyNoInteractions(sessions);
        work.remove().run();
        verify(sessions).cache(10L, "test", session);
        verify(schedules).pullAs(10L, "test", "term", "MANUAL", 10L, null, batch);
        assertNull(tasks.activeBatchId());
    }

    @Test void expiredSmsSessionReleasesTaskAndRecordsTerminalFailure() {
        when(schedules.pullWithCredentials(any(), any(), any(), any(), any(), any(), any())).thenThrow(new MfaRequiredException(mock(PendingCas.class), "phone"));
        when(schedules.progress(batch)).thenAnswer(call -> new HashMap<>());
        tasks.start(10L, 10L, "test", "password", null, batch); work.remove().run();
        String challenge = (String) tasks.progress(batch, 10L).get("challengeId");
        pending.remove(challenge);
        tasks.expirePending();
        assertNull(tasks.activeBatchId());
        verify(schedules).recordTaskState(eq(batch), eq("FAILED"), contains("超时"));
    }
}
