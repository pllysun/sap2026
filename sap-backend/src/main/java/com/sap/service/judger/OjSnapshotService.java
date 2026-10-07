package com.sap.service.judger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class OjSnapshotService {
    private final ObjectMapper json;
    private final OjExecutionSnapshotMapper snapshots;
    private final OjSubmissionMapper submissions;

    public synchronized String compact(Object value) {
        ObjectNode source=json.valueToTree(value);
        if(source.has("packRef")) return source.toString();
        ObjectNode shared=json.createObjectNode();
        for(String key:List.of("pack","languages","signature")) shared.set(key,source.path(key));
        String payload=canonical(shared).toString(), id=digest(payload);
        if(snapshots.selectById(id)==null) {
            OjExecutionSnapshot row=new OjExecutionSnapshot();row.setId(id);row.setPayloadJson(payload);row.setCreatedAt(LocalDateTime.now());
            try {snapshots.insert(row);} catch(DuplicateKeyException duplicate) {if(snapshots.selectById(id)==null)throw duplicate;}
        }
        source.remove(List.of("pack","languages"));source.put("packRef",id);
        return source.toString();
    }
    public JsonNode resolve(String value) throws Exception {
        ObjectNode source=(ObjectNode)json.readTree(value);
        if(!source.has("packRef")) return source; // All historical full snapshots remain readable.
        String id=source.path("packRef").asText();
        if(!id.matches("[a-f0-9]{64}"))throw new IllegalStateException("Invalid snapshot reference");
        OjExecutionSnapshot row=snapshots.selectById(id);
        if(row==null||!id.equals(digest(row.getPayloadJson())))throw new IllegalStateException("Snapshot missing or corrupt");
        ObjectNode shared=(ObjectNode)json.readTree(row.getPayloadJson());
        shared.setAll(source);shared.remove("packRef");return shared;
    }
    private JsonNode canonical(JsonNode node) {
        if(node.isObject()) {ObjectNode out=json.createObjectNode();TreeSet<String> keys=new TreeSet<>();node.fieldNames().forEachRemaining(keys::add);keys.forEach(k->out.set(k,canonical(node.get(k))));return out;}
        if(node.isArray()) {var out=json.createArrayNode();node.forEach(n->out.add(canonical(n)));return out;}
        return node;
    }
    static String digest(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    /** Small idempotent batches, with compare-and-swap: never change code or results. */
    @Scheduled(initialDelay=30000,fixedDelay=5000)
    public synchronized void compactHistory() {
        try {
            for(OjSubmission job:submissions.selectList(new LambdaQueryWrapper<OjSubmission>()
                .select(OjSubmission::getId,OjSubmission::getSnapshotJson,OjSubmission::getValidationSignature)
                .isNull(OjSubmission::getSnapshotKey).notIn(OjSubmission::getStatus,"QUEUED","RUNNING").orderByAsc(OjSubmission::getId).last("LIMIT 10"))) {
                String value=compact(json.readTree(job.getSnapshotJson()));JsonNode ref=json.readTree(value);
                submissions.update(null,new LambdaUpdateWrapper<OjSubmission>().eq(OjSubmission::getId,job.getId())
                    .isNull(OjSubmission::getSnapshotKey).notIn(OjSubmission::getStatus,"QUEUED","RUNNING")
                    .set(OjSubmission::getSnapshotKey,ref.path("packRef").asText()).set(OjSubmission::getSnapshotJson,value)
                    .set(job.getValidationSignature()==null,OjSubmission::getValidationSignature,ref.path("signature").asText()));
            }
        }catch(Exception e){log.warn("OJ snapshot compaction deferred ({})",e.getClass().getSimpleName());}
    }
}
