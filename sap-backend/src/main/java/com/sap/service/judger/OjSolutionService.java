package com.sap.service.judger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.dto.judger.*;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Editorials have their own revision-bound storage; adding one never changes judging data. */
@Service @RequiredArgsConstructor
public class OjSolutionService {
    private final OjSolutionMapper solutions;
    private final OjProblemMapper problems;
    private final OjProblemSetMapper sets;
    private final OjProblemSetItemMapper items;
    private final OjService oj;
    private final OjProblemSetService problemSets;

    long now(){return System.currentTimeMillis();}

    @Transactional(readOnly=true) public Map<String,Object> library(Long id,Long uid){
        oj.requireActiveAccount(uid);oj.detail(id,false); // Same visibility/signature rules as the statement.
        var p=oj.requireProblem(id);long now=now();Long end=embargo(id,now);
        if(end!=null)return locked(end,now);
        return document(id,p.getRevision(),oj.read(p.getPackJson(),ProblemPack.class).getModes(),now);
    }
    @Transactional(readOnly=true) public Map<String,Object> inSet(Long setId,Long itemId,Long uid){
        oj.requireActiveAccount(uid);
        var visible=problemSets.problem(setId,itemId,uid); // Checks access, phase, active item and frozen statement.
        var set=problemSets.require(setId);var item=items.selectById(itemId);long now=now();
        if("CONTEST".equals(set.getMode())&&(set.getEndsAt()==null||now<set.getEndsAt()))return locked(set.getEndsAt()==null?0L:set.getEndsAt(),now);
        Long otherEnd=embargo(item.getProblemId(),now);if(otherEnd!=null)return locked(otherEnd,now);
        @SuppressWarnings("unchecked") List<String> modes=(List<String>)visible.get("modes");
        return document(item.getProblemId(),item.getRevision(),modes,now);
    }
    // Enforce contest restrictions across both library and other set URLs, including different revisions.
    private Long embargo(Long problemId,long now){
        var links=items.selectList(new LambdaQueryWrapper<OjProblemSetItem>()
            .select(OjProblemSetItem::getProblemSetId).eq(OjProblemSetItem::getProblemId,problemId).eq(OjProblemSetItem::getActive,true));
        var ids=links.stream().map(OjProblemSetItem::getProblemSetId).distinct().toList();if(ids.isEmpty())return null;
        Long latest=null;
        for(var s:sets.selectBatchIds(ids))if("CONTEST".equals(s.getMode())&&List.of("PUBLISHED","ARCHIVED").contains(s.getStatus())&&(s.getEndsAt()==null||now<s.getEndsAt())){
            if(s.getEndsAt()==null)return 0L;latest=latest==null?s.getEndsAt():Math.max(latest,s.getEndsAt());
        }
        return latest;
    }
    private Map<String,Object> locked(Long end,long now){
        return Map.of("state","LOCKED","message","比赛结束后开放题解","unlocksAt",end,"serverNow",now);
    }
    private OjSolution find(Long id,Long revision){return solutions.selectOne(new LambdaQueryWrapper<OjSolution>()
        .eq(OjSolution::getProblemId,id).eq(OjSolution::getProblemRevision,revision));}
    private Map<String,Object> document(Long id,Long revision,List<String> modes,long now){
        var stored=find(id,revision);if(stored==null)return Map.of("state","MISSING","message","题解正在整理","serverNow",now);
        var d=oj.read(stored.getDocumentJson(),ProblemSolutionDocument.class);
        Map<String,Map<String,String>> codes=new LinkedHashMap<>();for(String mode:modes)if(d.getCodes().containsKey(mode))codes.put(mode,d.getCodes().get(mode));
        d.setCodes(codes);
        return Map.of("state","READY","document",d,"updatedAt",stored.getUpdatedAt(),"serverNow",now);
    }
    @Transactional(readOnly=true) public Map<String,Object> admin(Long id){
        var p=oj.requireProblem(id);return document(id,p.getRevision(),oj.read(p.getPackJson(),ProblemPack.class).getModes(),now());
    }
    @Transactional public synchronized Map<String,Object> save(Long id,ProblemSolutionDocument d){
        var p=problems.selectOne(new LambdaQueryWrapper<OjProblem>().eq(OjProblem::getId,id).last("FOR UPDATE"));
        if(p==null)throw new BusinessException(404,"题目不存在");
        if(d==null||!Objects.equals(p.getRevision(),d.getProblemRevision())||!Objects.equals(p.getSlug(),d.getProblemSlug())||!Objects.equals(p.getTitle(),d.getTitle()))
            throw new BusinessException(409,"题解与当前题目版本不一致，请重新加载");
        if(!oj.currentSignature().equals(p.getValidationSignature()))throw new BusinessException(400,"题目尚未通过当前运行环境的参考代码验证");
        var pack=oj.read(p.getPackJson(),ProblemPack.class);OjSolutionContent.validate(d,pack);
        String json=oj.write(d);if(json.length()>500000)throw new BusinessException(400,"题解过大");
        var s=find(id,p.getRevision());boolean create=s==null;if(create){s=new OjSolution();s.setProblemId(id);s.setProblemRevision(p.getRevision());}
        s.setDocumentJson(json);s.setUpdatedAt(LocalDateTime.now());if(create)solutions.insert(s);else solutions.updateById(s);
        return Map.of("id",s.getId(),"problemId",id,"problemRevision",p.getRevision(),"state","READY");
    }
    @Transactional public synchronized Map<String,Object> importDocuments(List<ProblemSolutionDocument> documents){
        if(documents==null||documents.isEmpty()||documents.size()>10)throw new BusinessException(400,"每批最多导入 10 篇题解");
        var seen=new HashSet<String>();var results=new ArrayList<Map<String,Object>>();
        for(var d:documents){
            if(d==null||d.getProblemSlug()==null||!seen.add(d.getProblemSlug()))throw new BusinessException(400,"题解批次包含空值或重复题目");
            var p=problems.selectOne(new LambdaQueryWrapper<OjProblem>().eq(OjProblem::getSlug,d.getProblemSlug()));
            if(p==null)throw new BusinessException(404,"题目不存在："+d.getProblemSlug());
            results.add(save(p.getId(),d));
        }
        return Map.of("count",results.size(),"records",results);
    }
}
