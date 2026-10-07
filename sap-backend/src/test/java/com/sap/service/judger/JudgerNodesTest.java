package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import com.sap.entity.judger.OjNode;
import com.sap.mapper.judger.OjNodeMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JudgerNodesTest {
    final OjNodeMapper mapper=mock(OjNodeMapper.class);
    final JudgerNodeTransport transport=mock(JudgerNodeTransport.class);
    final JudgerNodeSecrets secrets=mock(JudgerNodeSecrets.class);
    final JudgerProperties config=new JudgerProperties();
    final ObjectMapper json=new ObjectMapper();
    final List<OjNode> rows=new ArrayList<>();
    final AtomicLong clock=new AtomicLong(100000),ids=new AtomicLong();
    JudgerNodes pool;
    @BeforeEach void setup(){config.setEnabled(true);pool=new JudgerNodes(mapper,config,transport,secrets);pool.clock=clock::get;
        when(mapper.selectList(any())).thenAnswer(i->new ArrayList<>(rows));when(mapper.selectById(anyLong())).thenAnswer(i->rows.stream().filter(n->n.getId().equals(i.getArgument(0))).findFirst().orElse(null));
        when(secrets.token(any())).thenReturn("test-token");
        when(transport.request(any(),eq("POST"),eq("/leases"),any(),anyInt(),isNull())).thenAnswer(i->json.valueToTree(Map.of("lease",String.format("%032d",ids.incrementAndGet()))));
    }
    @AfterEach void teardown(){pool.shutdown();}
    OjNode node(long id,int cap){OjNode n=new OjNode();n.setId(id);n.setName("node-"+id);n.setEndpoint("http://10.0.0."+id+":5051");n.setBuiltin(false);n.setEnabled(true);n.setMaxConcurrency(cap);n.setTokenCipher("PRIVATE_CIPHER");rows.add(n);report(n,cap,0,"RUNNING",config.getRuntimeId());return n;}
    void report(OjNode n,int cap,int used,String state,String runtime){pool.heartbeat(n.getId(),"Bearer test-token",json.valueToTree(Map.of("protocol",1,"capacity",cap,"active",used,"freeSlots",Math.max(0,cap-used),"state",state,"runtimeId",runtime)));}
    @Test void strongerNodeReceivesMoreTasksWithoutOversubscription(){node(1,1);node(2,4);List<JudgerNodes.Lease> leases=new ArrayList<>();for(int i=0;i<5;i++)leases.add(pool.acquire(Set.of()));assertTrue(leases.stream().allMatch(Objects::nonNull));assertEquals(4,leases.stream().filter(l->l.node.getId()==2).count());assertEquals(0,pool.freeSlots());assertNull(pool.acquire(Set.of()));leases.forEach(JudgerNodes.Lease::close);}
    @Test void disabledFullIncompatibleAndStaleNodesCannotReceiveWork(){OjNode n=node(1,3);n.setEnabled(false);assertNull(pool.acquire(Set.of()));n.setEnabled(true);report(n,3,3,"RUNNING",config.getRuntimeId());assertEquals(0,pool.freeSlots());report(n,3,0,"RUNNING","wrong-runtime");assertEquals("INCOMPATIBLE",pool.view(n).get("state"));assertNull(pool.acquire(Set.of()));report(n,3,0,"RUNNING",config.getRuntimeId());clock.addAndGet(15001);assertEquals("OFFLINE",pool.view(n).get("state"));assertNull(pool.acquire(Set.of()));}
    @Test void connectionFailureTriesAnotherNode(){OjNode weak=node(1,1),strong=node(2,4);when(transport.request(eq(strong),eq("POST"),eq("/leases"),any(),anyInt(),isNull())).thenThrow(new NodeUnavailableException());var lease=pool.acquire(Set.of());assertNotNull(lease);assertEquals(weak.getId(),lease.node.getId());assertEquals("OFFLINE",pool.view(strong).get("state"));lease.close();}
    @Test void usedNodeIsExcludedFromRetryAndLeasePinsEntireExecution(){node(1,1);node(2,2);var lease=pool.acquire(Set.of(2L));assertEquals(1,lease.node.getId());lease.bind();assertSame(lease,pool.currentLease());lease.close();assertNull(pool.currentLease());}
    @Test void interruptedShutdownReleasesNodeSlotWithoutLosingInterrupt(){
        node(1,1);var lease=pool.acquire(Set.of());pool.shutdown();
        when(transport.request(any(),eq("DELETE"),anyString(),isNull(),eq(2),isNull())).thenAnswer(i->{assertFalse(Thread.currentThread().isInterrupted());return json.createObjectNode();});
        Thread.currentThread().interrupt();
        try{assertDoesNotThrow(lease::close);assertTrue(Thread.currentThread().isInterrupted());verify(transport).request(eq(lease.node),eq("DELETE"),eq("/leases/"+lease.id),isNull(),eq(2),isNull());}
        finally{Thread.interrupted();}
    }
    @Test void heartbeatUsesIndependentMachineAuthenticationAndDoesNotExposeCredentials() throws Exception {OjNode n=node(1,1);assertEquals(401,assertThrows(BusinessException.class,()->pool.heartbeat(1L,"Bearer other",json.createObjectNode())).getCode());assertThrows(BusinessException.class,()->pool.heartbeat(1L,null,json.createObjectNode()));assertFalse(json.writeValueAsString(pool.overview()).contains("PRIVATE_CIPHER"));assertFalse(json.writeValueAsString(n).contains("tokenCipher"));}
    @Test void configuredAndPlatformLimitsOverrideInflatedReports(){OjNode n=node(1,2);report(n,999,0,"RUNNING",config.getRuntimeId());assertEquals(2,pool.freeSlots());node(2,32);node(3,32);assertEquals(32,pool.overview().get("capacity"));assertEquals(32,pool.freeSlots());}
    @Test void stopDisablesSchedulingEvenIfAgentIsDisconnected(){OjNode n=node(1,1);when(transport.request(eq(n),eq("POST"),eq("/stop"),any(),anyInt(),isNull())).thenThrow(new NodeUnavailableException());assertThrows(BusinessException.class,()->pool.control(1L,"stop"));assertFalse(n.getEnabled());assertEquals(0,pool.freeSlots());verify(mapper).updateById(n);}
    @Test void rejectsUnsafeEndpoints(){for(String endpoint:List.of("http://127.0.0.1:5051","http://169.254.169.254","http://8.8.8.8","file:///etc/passwd","https://user:pass@10.0.0.1","http://10.0.0.1/run"))assertThrows(BusinessException.class,()->JudgerNodes.validateEndpoint(endpoint));assertDoesNotThrow(()->JudgerNodes.validateEndpoint("http://10.0.0.2:5051"));}
    @Test void tokensSurviveServiceRestartAndAreEncrypted(@TempDir Path directory)throws Exception{JudgerProperties cfg=new JudgerProperties();cfg.setNodeKeyFile(directory.resolve("key").toString());JudgerNodeSecrets first=new JudgerNodeSecrets(cfg);String token="a".repeat(48);String cipher=first.encrypt(token);assertNotEquals(token,cipher);OjNode n=new OjNode();n.setBuiltin(false);n.setTokenCipher(cipher);assertEquals(token,new JudgerNodeSecrets(cfg).token(n));assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(Path.of(cfg.getNodeKeyFile()))));}
    @Test void resourceFieldsRemainScalarsThroughApplicationJsonConverter() throws Exception {
        OjNode n=node(1,1);var report=json.createObjectNode().put("protocol",1).put("runtimeId",config.getRuntimeId())
            .put("state","RUNNING").put("capacity",1).put("freeSlots",1).put("cpuPercent",12.5).put("memoryMb",640).put("error","");
        pool.heartbeat(n.getId(),"Bearer test-token",report);
        var encoded=com.alibaba.fastjson2.JSON.parseObject(com.alibaba.fastjson2.JSON.toJSONString(pool.view(n)));
        assertEquals(12.5,encoded.getDoubleValue("cpuPercent"));assertEquals(640,encoded.getIntValue("memoryMb"));
        assertEquals("",encoded.getString("error"));assertFalse(encoded.containsKey("tokenCipher"));
        assertFalse(com.alibaba.fastjson2.JSON.toJSONString(n).contains("PRIVATE_CIPHER"));
    }
}
