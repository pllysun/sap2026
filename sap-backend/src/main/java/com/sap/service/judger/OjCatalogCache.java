package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/** Single-instance, bounded, short-lived public metadata only. Each hit is a fresh copy. */
final class OjCatalogCache {
    private record Entry(byte[] json,long expiresAt,long weight) {}
    private final ObjectMapper json;
    private final long maxBytes,ttlNanos;
    private final LongSupplier clock;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private long bytes;

    OjCatalogCache(ObjectMapper json) {this(json,5*1024*1024,30_000_000_000L,System::nanoTime);}
    OjCatalogCache(ObjectMapper json,long maxBytes,long ttlNanos,LongSupplier clock) {
        this.json=json;this.maxBytes=maxBytes;this.ttlNanos=ttlNanos;this.clock=clock;
    }
    synchronized <T> T get(String key,Class<T> type,Supplier<T> load) {
        // A write transaction must not publish its uncommitted catalogue to other accounts.
        if(TransactionSynchronizationManager.isActualTransactionActive())return load.get();
        long now=clock.getAsLong();
        entries.entrySet().removeIf(row->{if(row.getValue().expiresAt<=now){bytes-=row.getValue().weight;return true;}return false;});
        Entry cached=entries.get(key);
        try {
            if(cached!=null)return json.readValue(cached.json,type);
            T value=load.get();byte[] encoded=json.writeValueAsBytes(value);
            long weight=encoded.length+2L*key.length()+256;
            if(weight<=maxBytes) {
                while(!entries.isEmpty()&&(bytes+weight>maxBytes||entries.size()>=128)) {
                    var iterator=entries.entrySet().iterator();bytes-=iterator.next().getValue().weight;iterator.remove();
                }
                entries.put(key,new Entry(encoded,clock.getAsLong()+ttlNanos,weight));bytes+=weight;
            }
            return value;
        }catch(java.io.IOException e){throw new IllegalStateException("题库摘要缓存无法读取",e);}
    }
    synchronized void invalidate() {entries.clear();bytes=0;}
    void invalidateAfterCommit() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){invalidate();}
            });
        }else invalidate();
    }
    synchronized long bytes(){return bytes;}
    synchronized int size(){return entries.size();}
}
