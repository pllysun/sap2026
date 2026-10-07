package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.entity.judger.OjExecutionSnapshot;
import com.sap.mapper.judger.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OjSnapshotTest {
    final ObjectMapper json=new ObjectMapper();final Map<String,OjExecutionSnapshot> stored=new HashMap<>();
    final OjExecutionSnapshotMapper mapper=mock(OjExecutionSnapshotMapper.class);
    final OjSnapshotService service=new OjSnapshotService(json,mapper,mock(OjSubmissionMapper.class));
    OjSnapshotTest(){when(mapper.selectById(anyString())).thenAnswer(i->stored.get(i.getArgument(0)));when(mapper.insert(any(OjExecutionSnapshot.class))).thenAnswer(i->{var row=(OjExecutionSnapshot)i.getArgument(0);stored.put(row.getId(),row);return 1;});}
    Map<String,Object> snapshot(String input){return Map.of("pack",Map.of("tests","private-".repeat(20000),"reference","private-source"),"languages",List.of(Map.of("key","cpp")),"signature","verified","custom",!input.isEmpty(),"input",input);}
    @Test void repeatedSnapshotsStorePrivateTestsOnceAndKeepInputsSeparate() throws Exception {
        String a=service.compact(snapshot("")),b=service.compact(snapshot("42"));assertEquals(1,stored.size());verify(mapper,times(1)).insert(any(OjExecutionSnapshot.class));
        assertTrue(a.length()<200);assertFalse(a.contains("private-"));assertEquals("",service.resolve(a).path("input").asText());assertEquals("42",service.resolve(b).path("input").asText());
        assertEquals(json.valueToTree(snapshot("42")),service.resolve(b));
    }
    @Test void immutableInputsChangeDigestButMapKeyOrderDoesNot(){
        var reversed=new LinkedHashMap<>(snapshot(""));var pack=new LinkedHashMap<String,Object>();pack.put("reference","private-source");pack.put("tests","private-".repeat(20000));reversed.put("pack",pack);
        assertEquals(service.compact(snapshot("")),service.compact(reversed));reversed.put("languages",List.of(Map.of("key","java")));assertNotEquals(service.compact(snapshot("")),service.compact(reversed));assertEquals(2,stored.size());
    }
    @Test void historicalFullSnapshotAndCompactIdempotencyRemainReadable() throws Exception {
        String old=json.writeValueAsString(snapshot(""));assertEquals(json.readTree(old),service.resolve(old));String ref=service.compact(snapshot(""));assertEquals(ref,service.compact(json.readTree(ref)));
    }
    @Test void missingOrModifiedSharedDataFailsClosedInsteadOfUsingLatestProblem() throws Exception {
        String ref=service.compact(snapshot(""));stored.values().iterator().next().setPayloadJson("{}");assertThrows(IllegalStateException.class,()->service.resolve(ref));stored.clear();assertThrows(IllegalStateException.class,()->service.resolve(ref));
    }
}
