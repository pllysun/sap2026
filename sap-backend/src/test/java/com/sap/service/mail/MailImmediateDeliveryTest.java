package com.sap.service.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.service.EmailService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailImmediateDeliveryTest {
    static final String KEY="0123456789abcdef0123456789abcdef";
    JdbcTemplate db; MailStore store; EmailService smtp; MailQueue mail;
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        db=new JdbcTemplate(ds);store=new MailStore(db,new DataSourceTransactionManager(ds));store.init();
        db.execute("CREATE TABLE sys_email_template(id BIGINT PRIMARY KEY,template_key VARCHAR(80),deleted INT)");
        smtp=mock(EmailService.class);mail=new MailQueue(store,smtp,new ObjectMapper());
        ReflectionTestUtils.setField(mail,"key",KEY);
    }
    @AfterEach void stop(){mail.stop();}
    long outstanding(){return db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue",Long.class);}
    void drained() throws InterruptedException {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(outstanding()>0 && System.nanoTime()<until) Thread.sleep(10);
        assertEquals(0,outstanding());
    }
    @Test void fiveMessagesStartTogetherEvenWhenEarlierSmtpCallsHaveNotCompleted() throws Exception {
        var started=new CountDownLatch(5);var release=new CountDownLatch(1);
        doAnswer(call->{started.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;})
                .when(smtp).deliver(anyString(),anyString(),anyString());
        for(int i=0;i<5;i++)mail.enqueueTest("user"+i+"@example.test","Concurrent "+i,"<p>test</p>",null);
        verify(smtp,never()).deliver(anyString(),anyString(),anyString());
        db.update("UPDATE sys_mail_worker SET next_at=?",System.currentTimeMillis()+65000);
        when(smtp.sendingEnabled()).thenReturn(true);
        try {
            mail.kick();assertTrue(started.await(5,TimeUnit.SECONDS),"No message may wait for another SMTP call or the old 65-second gate");
            assertEquals(5,((Number)mail.status().get("sending")).longValue());
        } finally {release.countDown();}
        drained();verify(smtp,times(5)).deliver(anyString(),anyString(),anyString());
        assertEquals(5,db.queryForObject("SELECT COUNT(*) FROM sys_mail_log WHERE status='SUCCESS'",Integer.class));
    }
    @Test void sendsOnlyAfterBusinessTransactionCommitsAndNeverAfterRollback() throws Exception {
        when(smtp.sendingEnabled()).thenReturn(true);var sent=new CountDownLatch(1);
        doAnswer(call->{sent.countDown();return null;}).when(smtp).deliver(anyString(),anyString(),anyString());
        store.transaction(()->{
            mail.enqueueTest("user@example.test","Committed","<p>test</p>",null);
            try {assertFalse(sent.await(100,TimeUnit.MILLISECONDS));}catch(InterruptedException e){throw new AssertionError(e);}
            return null;
        });
        assertTrue(sent.await(5,TimeUnit.SECONDS));drained();
        assertThrows(IllegalStateException.class,()->store.transaction(()->{
            mail.enqueueTest("user@example.test","Rolled back","<p>test</p>",null);throw new IllegalStateException("rollback");
        }));
        mail.kick();verify(smtp,never()).deliver(anyString(),eq("Rolled back"),anyString());assertEquals(0,outstanding());
    }
    @Test void twoWorkersCannotClaimTheSameMessage() throws Exception {
        mail.enqueueTest("user@example.test","Claim once","<p>test</p>",null);
        var other=new MailQueue(store,smtp,new ObjectMapper());var pool=Executors.newFixedThreadPool(4);
        try {
            var claims=pool.invokeAll(List.<Callable<Map<String,Object>>>of(mail::claim,other::claim,mail::claim,other::claim));
            var winners=new ArrayList<Map<String,Object>>();for(var claim:claims)if(claim.get()!=null)winners.add(claim.get());
            assertEquals(1,winners.size());mail.finish(winners.getFirst(),"SUCCESS",null);assertEquals(0,outstanding());
        } finally {pool.shutdownNow();other.stop();}
    }
    @Test void oldGlobalLeaseMigratesToOneMessageWithoutBlockingOtherMessages() {
        mail.enqueueTest("user@example.test","Legacy in flight","<p>test</p>",null);
        long lease=System.currentTimeMillis()+60000;
        db.update("UPDATE sys_mail_queue SET state='SENDING',owner='legacy-owner',lease_until=0");
        db.update("UPDATE sys_mail_worker SET owner='legacy-owner',lease_until=?,next_at=?",lease,lease);
        mail.start();
        assertEquals(lease,db.queryForObject("SELECT lease_until FROM sys_mail_queue",Long.class));
        mail.enqueueTest("other@example.test","New message","<p>test</p>",null);
        var fresh=mail.claim();assertNotNull(fresh);assertEquals("New message",fresh.get("subject"));mail.finish(fresh,"SUCCESS",null);
        db.update("UPDATE sys_mail_queue SET lease_until=0");assertNull(mail.claim());assertEquals(0,outstanding());
        assertEquals("UNKNOWN",db.queryForObject("SELECT status FROM sys_mail_failed",String.class));
        verify(smtp,never()).deliver(anyString(),anyString(),anyString());
    }
    @Test void existingQueueSchemaGainsPerMessageLeasesWithoutLosingMail() {
        mail.enqueueTest("user@example.test","Persisted message","<p>test</p>",null);
        db.execute("DROP INDEX idx_mail_queue_lease");db.execute("ALTER TABLE sys_mail_queue DROP COLUMN lease_until");
        store.init();assertEquals(1,outstanding());assertEquals(0,db.queryForObject("SELECT lease_until FROM sys_mail_queue",Long.class));
        var job=mail.claim();assertNotNull(job);assertTrue(((Number)job.get("lease_until")).longValue()>System.currentTimeMillis());
    }
}
