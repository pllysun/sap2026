package com.sap.service.judger;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.dto.judger.LearningAction;
import com.sap.entity.judger.OjLearningProfile;
import com.sap.mapper.judger.*;
import com.sap.vo.judger.OjLearningStats;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class OjLearningService {
    private final OjService oj;
    private final OjProgressService progress;
    private final OjSubmissionMapper submissions;
    private final OjLearningProfileMapper profiles;
    private final ObjectMapper json;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final int[] INTERVALS={1,3,7,14,30,60};
    private static final Set<String> TRACKS=Set.of("all","starter","sequence","structure","methods","explore");
    // Preserve existing learning archives when replacing the old competition tracks.
    private static final Map<String,String> LEGACY_TRACKS=Map.of("lanqiao","starter","gplt","sequence","icpc","explore","noip","structure","nowcoder","methods");

    @SuppressWarnings("unchecked")
    public Map<String,Object> overview(Long userId) {
        oj.requireActiveAccount(userId);
        var catalog=progress.library(oj.learningCatalog(),userId);
        var rows=(List<Map<String,Object>>)catalog.get("records");
        var ids=rows.stream().map(r->((Number)r.get("id")).longValue()).toList();
        Map<Long,OjLearningStats> stats=new HashMap<>();
        if(!ids.isEmpty())for(var s:submissions.learningStats(userId,ids))stats.put(s.getProblemId(),s);
        for(var row:rows) {
            var s=stats.get(((Number)row.get("id")).longValue());
            Map<String,Object> view=new LinkedHashMap<>();
            view.put("attempts",s==null?0:s.getAttempts());view.put("acAttempts",s==null?0:s.getAcAttempts());
            view.put("firstAcceptedAt",s==null?null:s.getFirstAcceptedAt());view.put("lastSubmittedAt",s==null?null:s.getLastSubmittedAt());
            row.put("practice",view);
            row.remove("sortOrder");row.remove("status");
        }
        var profile=profiles.selectById(userId);
        var state=read(profile);hideUnavailable(state,new HashSet<>(ids));
        LocalDate today=LocalDate.now(ZONE);
        var days=ids.isEmpty()?List.of():submissions.learningDays(userId,ids,today.minusDays(59).atStartOfDay()).stream()
            .map(day->Map.of("day",day.get("activity_date").toString(),"attempts",day.get("attempts"),"accepted",day.get("accepted"))).toList();
        return Map.of("problems",rows,"total",catalog.get("total"),"truncated",((Number)catalog.get("total")).intValue()>rows.size(),
            "state",state,"version",profile==null?0L:profile.getRevision(),"serverDate",today.toString(),
            "days",days);
    }

    @Transactional
    public Map<String,Object> update(Long userId,LearningAction action) {
        oj.requireActiveAccount(userId);
        var current=profiles.selectById(userId);
        long version=current==null?0:current.getRevision();
        if(!Objects.equals(action.getVersion(),version))conflict();
        OjLearningState state=read(current);
        switch(action.getAction()) {
            case "TRACK" -> {String track=normalizeTrack(action.getTrackId());if(!TRACKS.contains(track))bad("学习方向无效");state.setTrackId(track);}
            case "PLAN" -> {
                var ids=action.getProblemIds();
                if(ids==null||ids.isEmpty()||action.getDailyTarget()==null)bad("请选择题目和每日目标");
                if(new HashSet<>(ids).size()!=ids.size())bad("计划题目不能重复");
                requireVisible(ids);
                var rest=action.getRestDays()==null?List.<Integer>of():action.getRestDays().stream().distinct().sorted().toList();
                state.setPlan(new OjLearningState.Plan(action.getTitle()==null||action.getTitle().isBlank()?"我的训练计划":action.getTitle().strip(),
                    action.getDailyTarget(),rest,new ArrayList<>(ids),LocalDate.now(ZONE).toString()));
            }
            case "PLAN_CLEAR" -> state.setPlan(null);
            case "BOOKMARK" -> {
                Long id=requireProblem(action);
                if(action.getBookmarked()==null)bad("请指定收藏状态");
                state.getBookmarks().remove(id);
                if(Boolean.TRUE.equals(action.getBookmarked()))state.getBookmarks().add(id);
                if(state.getBookmarks().size()>500)bad("最多收藏 500 道题");
            }
            case "NOTE" -> {
                String id=requireProblem(action).toString();String text=action.getText()==null?"":action.getText().strip();
                if(text.isEmpty())state.getNotes().remove(id);else state.getNotes().put(id,text);
                if(state.getNotes().size()>100)bad("最多保存 100 道题的笔记");
            }
            case "REVIEW" -> {
                Long id=requireProblem(action);
                if(action.getEase()==null)bad("请选择复习反馈");
                var stats=submissions.learningStats(userId,List.of(id));
                if(stats.isEmpty()||stats.getFirst().getFirstAcceptedAt()==null)bad("正式提交通过后才能记录复习反馈");
                var previous=state.getReviews().get(id.toString());
                int stage=nextStage(previous==null?1:previous.stage(),action.getEase());
                state.getReviews().put(id.toString(),new OjLearningState.Review(stage,LocalDate.now(ZONE).plusDays(INTERVALS[stage-1]).toString()));
            }
            default -> bad("操作无效");
        }
        String encoded=write(state);
        if(encoded.getBytes(StandardCharsets.UTF_8).length>200_000)bad("笔记总量过大，请删减后再保存");
        LocalDateTime now=LocalDateTime.now(ZONE);
        if(current==null) {
            var profile=new OjLearningProfile();profile.setUserId(userId);profile.setRevision(1L);profile.setStateJson(encoded);profile.setUpdatedAt(now);
            try{profiles.insert(profile);}catch(DuplicateKeyException e){conflict();}
        }else {
            int changed=profiles.update(null,new LambdaUpdateWrapper<OjLearningProfile>().eq(OjLearningProfile::getUserId,userId)
                .eq(OjLearningProfile::getRevision,version).set(OjLearningProfile::getRevision,version+1)
                .set(OjLearningProfile::getStateJson,encoded).set(OjLearningProfile::getUpdatedAt,now));
            if(changed!=1)conflict();
        }
        // A fresh visibility check also prevents old notes exposing newly hidden problems.
        return Map.of("version",version+1);
    }
    static int nextStage(int stage,String ease){return Math.max(1,Math.min(6,stage+("hard".equals(ease)?-1:"easy".equals(ease)?2:1)));}
    private Long requireProblem(LearningAction action){if(action.getProblemId()==null)bad("请选择题目");requireVisible(List.of(action.getProblemId()));return action.getProblemId();}
    private void requireVisible(List<Long> ids){if(!new HashSet<>(oj.visibleCatalogIds(ids)).containsAll(ids))throw new BusinessException(404,"题目尚未发布或已停用");}
    private OjLearningState read(OjLearningProfile profile){
        if(profile==null){var state=new OjLearningState();state.setTrackId("starter");return state;}
        try{var state=json.readValue(profile.getStateJson(),OjLearningState.class);String track=normalizeTrack(state.getTrackId());state.setTrackId(TRACKS.contains(track)?track:"starter");return state;}
        catch(Exception e){throw new IllegalStateException("学习档案无法读取",e);}
    }
    private static String normalizeTrack(String track){return LEGACY_TRACKS.getOrDefault(track==null?"":track,track==null?"":track);}
    private String write(OjLearningState state){try{return json.writeValueAsString(state);}catch(Exception e){throw new IllegalStateException(e);}}
    private void hideUnavailable(OjLearningState state,Set<Long> ids){
        state.getBookmarks().removeIf(id->!ids.contains(id));
        state.getNotes().keySet().removeIf(id->!ids.contains(Long.valueOf(id)));
        state.getReviews().keySet().removeIf(id->!ids.contains(Long.valueOf(id)));
        var plan=state.getPlan();if(plan!=null)state.setPlan(new OjLearningState.Plan(plan.title(),plan.dailyTarget(),plan.restDays(),plan.problemIds().stream().filter(ids::contains).toList(),plan.startedOn()));
    }
    private static void bad(String message){throw new BusinessException(message);}
    private static void conflict(){throw new BusinessException(409,"学习档案已在其他页面更新，请刷新后重试");}
}
