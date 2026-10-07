package com.sap.service.judger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class OjCatalogCacheTest {
    @Test void cacheIsBoundedExpiresAndDoesNotRetainMutations() {
        var time=new AtomicLong();var cache=new OjCatalogCache(new ObjectMapper(),1024,10,time::get);var count=new AtomicInteger();
        var first=cache.get("page",Map.class,()->{count.incrementAndGet();return new HashMap<>(Map.of("title","public"));});first.put("private","AC");assertFalse(cache.get("page",Map.class,()->{throw new AssertionError();}).containsKey("private"));time.set(11);cache.get("page",Map.class,()->{count.incrementAndGet();return Map.of("title","updated");});assertEquals(2,count.get());
        for(int i=0;i<50;i++)cache.get("key"+i,String.class,()->"X".repeat(200));assertTrue(cache.bytes()<=1024);cache.get("oversized",String.class,()->"X".repeat(2000));assertTrue(cache.bytes()<=1024);cache.invalidate();assertEquals(0,cache.bytes());assertEquals(0,cache.size());
    }
    @Test void concurrentFirstVisitsShareOneLoad() throws Exception {
        var cache=new OjCatalogCache(new ObjectMapper());var count=new AtomicInteger();try(var executor=Executors.newFixedThreadPool(10)){var futures=new ArrayList<Future<String>>();for(int i=0;i<20;i++)futures.add(executor.submit(()->cache.get("page",String.class,()->{count.incrementAndGet();return "public";})));for(var f:futures)assertEquals("public",f.get(5,TimeUnit.SECONDS));}assertEquals(1,count.get());
    }
    @Test void uncommittedTransactionsBypassCacheAndOnlyCommitInvalidatesIt() {
        var cache=new OjCatalogCache(new ObjectMapper());cache.get("page",String.class,()->"published");TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertEquals("draft",cache.get("page",String.class,()->"draft"));cache.invalidateAfterCommit();assertEquals(1,cache.size());TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);}finally{TransactionSynchronizationManager.clear();}
        assertEquals("published",cache.get("page",String.class,()->{throw new AssertionError();}));TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);
        try{cache.invalidateAfterCommit();TransactionSynchronizationUtils.triggerAfterCommit();assertEquals(0,cache.size());}finally{TransactionSynchronizationManager.clear();}
    }
}
