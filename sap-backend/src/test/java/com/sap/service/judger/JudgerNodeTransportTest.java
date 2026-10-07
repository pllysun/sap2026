package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.entity.judger.OjNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JudgerNodeTransportTest {
    HttpServer server;
    ExecutorService workers;
    OjNode node;
    JudgerNodeTransport transport;

    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        workers=Executors.newCachedThreadPool();server.setExecutor(workers);server.start();
        node=new OjNode();node.setEndpoint("http://127.0.0.1:"+server.getAddress().getPort());
        var secrets=mock(JudgerNodeSecrets.class);when(secrets.token(node)).thenReturn("local-fixture-token");
        transport=new JudgerNodeTransport(new ObjectMapper(),secrets);
    }
    @AfterEach void cleanup() {server.stop(0);workers.shutdownNow();}

    @Test void subscriberCancelsBeforeRetainingExcessIncludingMultibyteData() throws Exception {
        var subscriber=new BoundedResponseSubscriber(6);var subscription=mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap("中文".getBytes(StandardCharsets.UTF_8))));
        ByteBuffer excess=ByteBuffer.wrap(new byte[1024]);subscriber.onNext(List.of(excess));
        assertEquals(0,excess.position());verify(subscription).cancel();
        assertThrows(ExecutionException.class,()->subscriber.getBody().toCompletableFuture().get());
    }
    @Test void subscriberAcceptsExactlyTheLimitAcrossChunks() throws Exception {
        var subscriber=new BoundedResponseSubscriber(6);var subscription=mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}),ByteBuffer.wrap(new byte[]{3})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{4,5,6})));subscriber.onComplete();
        assertArrayEquals(new byte[]{1,2,3,4,5,6},subscriber.getBody().toCompletableFuture().get());
        verify(subscription,never()).cancel();
    }
    @Test void normalUnicodeResponseAndAuthorizationArePreserved() {
        server.createContext("/status",exchange->{
            assertEquals("Bearer local-fixture-token",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body="{\"message\":\"中文🙂\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        assertEquals("中文🙂",transport.request(node,"GET","/status",null,2,null).path("message").asText());
    }
    @Test void capacityErrorStillHasItsOwnType() {
        server.createContext("/leases",exchange->{exchange.sendResponseHeaders(409,-1);exchange.close();});
        assertThrows(JudgerNodeTransport.NodeCapacityException.class,()->transport.request(node,"POST","/leases",null,2,null));
    }
    @Test void oversizedFixedAndChunkedResponsesAreCancelled() throws Exception {
        for(boolean chunked:List.of(false,true)) {
            String path="/large-"+chunked;
            CountDownLatch cancelled=new CountDownLatch(1);
            server.createContext(path,exchange->{
                try {
                    exchange.sendResponseHeaders(200,chunked?0:16*1024*1024);
                    byte[] chunk=new byte[16384];
                    for(int i=0;i<1024;i++){exchange.getResponseBody().write(chunk);exchange.getResponseBody().flush();}
                } catch(IOException expected) {cancelled.countDown();}
                finally {exchange.close();}
            });
            assertThrows(NodeUnavailableException.class,()->transport.request(node,"GET",path,null,3,null));
            assertTrue(cancelled.await(2,TimeUnit.SECONDS),"Peer should observe cancellation before sending all 16 MiB");
        }
    }
    @Test void executionResponseSupportsMaximumEscapedStreams() {
        server.createContext("/engine/run",exchange->{
            byte[] data=new ObjectMapper().writeValueAsBytes(List.of(java.util.Map.of("status","Accepted","files",
                java.util.Map.of("stdout","\u0000".repeat(262144),"stderr","\u0001".repeat(65536)))));
            exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write(data);exchange.close();
        });
        var response=transport.request(node,"POST","/engine/run",java.util.Map.of(),3,"fixture-lease");
        assertEquals(262144,response.get(0).path("files").path("stdout").asText().length());
    }
    @Test void deadlineCoversTricklingBodyAfterHeaders() {
        server.createContext("/slow",exchange->{
            try {
                exchange.sendResponseHeaders(200,0);
                for(int i=0;i<100;i++){exchange.getResponseBody().write(' ');exchange.getResponseBody().flush();Thread.sleep(100);}
            } catch(IOException ignored) {} catch(InterruptedException ignored) {Thread.currentThread().interrupt();}
            finally {exchange.close();}
        });
        long started=System.nanoTime();
        assertThrows(NodeUnavailableException.class,()->transport.request(node,"GET","/slow",null,1,null));
        assertTrue(System.nanoTime()-started<TimeUnit.MILLISECONDS.toNanos(2500));
    }
    @Test void interruptionIsRestored() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(NodeUnavailableException.class,()->transport.request(node,"GET","/status",null,1,null));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {Thread.interrupted();}
    }
}
