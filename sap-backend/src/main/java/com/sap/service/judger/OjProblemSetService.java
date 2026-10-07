package com.sap.service.judger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import com.sap.dto.judger.*;
import com.sap.entity.User;
import com.sap.entity.judger.*;
import com.sap.mapper.UserMapper;
import com.sap.mapper.judger.*;
import com.sap.vo.judger.ProblemSetScore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class OjProblemSetService {
    private final OjProblemSetMapper sets;
    private final OjProblemSetItemMapper items;
    private final OjProblemSetParticipantMapper participants;
    private final OjProblemSetAuditMapper audits;
    private final OjSubmissionMapper submissions;
    private final UserMapper users;
    private final OjService oj;
    private final OjContestScoringService scoring;
    private final JudgerProperties config;
    private final JdbcTemplate db;

    private static void fail(String message){throw new BusinessException(400,message);}
    public OjProblemSet require(Long id){var s=sets.selectById(id);if(s==null)throw new BusinessException(404,"题单不存在");return s;}
    private OjProblemSet lock(Long id){var s=sets.selectOne(new LambdaQueryWrapper<OjProblemSet>().eq(OjProblemSet::getId,id).last("FOR UPDATE"));if(s==null)throw new BusinessException(404,"题单不存在");return s;}
    private User user(Long id){var u=users.selectById(id);if(u==null||!Objects.equals(u.getStatus(),1))throw new BusinessException(403,"账号不可用");return u;}
    @SuppressWarnings("unchecked") private List<String> strings(String json){return oj.read(json==null?"[]":json,List.class);}
    private boolean allowed(OjProblemSet s,User u){return "PUBLIC".equals(s.getAccessType())||strings(s.getStudentsJson()).contains(u.getStudentId());}
    private OjProblemSet readable(Long id,Long uid,boolean admin){
        OjProblemSet s=require(id);
        if(!admin && (!List.of("PUBLISHED","ARCHIVED").contains(s.getStatus())||!allowed(s,user(uid))))throw new BusinessException(404,"题单不存在或无权访问");
        return s;
    }
    public String phase(OjProblemSet s,long now){
        if(!"PUBLISHED".equals(s.getStatus()))return s.getStatus();
        if(!"CONTEST".equals(s.getMode()))return "PRACTICE";
        if(s.getStartsAt()==null||s.getEndsAt()==null)return "DRAFT";
        if(now<s.getStartsAt())return "UPCOMING";
        return now<s.getEndsAt()?"RUNNING":"ENDED";
    }
    private List<OjProblemSetItem> metadata(Long id){return items.selectList(new LambdaQueryWrapper<OjProblemSetItem>()
        .select(OjProblemSetItem::getId,OjProblemSetItem::getProblemSetId,OjProblemSetItem::getProblemId,OjProblemSetItem::getSortOrder,
            OjProblemSetItem::getTitle,OjProblemSetItem::getDifficulty,OjProblemSetItem::getSourcePlatform,OjProblemSetItem::getTagsJson,
            OjProblemSetItem::getModesJson,OjProblemSetItem::getRevision,OjProblemSetItem::getValidationSignature,OjProblemSetItem::getActive,OjProblemSetItem::getCancelReason)
        .eq(OjProblemSetItem::getProblemSetId,id).orderByAsc(OjProblemSetItem::getSortOrder,OjProblemSetItem::getId));}
    private OjProblemSetItem item(Long set,Long id){var i=items.selectById(id);if(i==null||!Objects.equals(i.getProblemSetId(),set))throw new BusinessException(404,"题单题目不存在");return i;}
    private OjProblemSetParticipant participant(Long set,Long uid){return participants.selectOne(new LambdaQueryWrapper<OjProblemSetParticipant>().eq(OjProblemSetParticipant::getProblemSetId,set).eq(OjProblemSetParticipant::getUserId,uid));}
    private boolean questionsVisible(OjProblemSet s,long now){return !"CONTEST".equals(s.getMode())||s.getStartsAt()!=null&&now>=s.getStartsAt();}
    private Map<String,Object> view(OjProblemSet s,Long uid,boolean admin){
        long now=System.currentTimeMillis();var list=metadata(s.getId());long total=list.stream().filter(i->Boolean.TRUE.equals(i.getActive())).count();
        Map<String,Object> v=new LinkedHashMap<>();v.put("id",s.getId());v.put("name",s.getName());v.put("description",s.getDescription());
        v.put("mode",s.getMode());v.put("status",s.getStatus());v.put("phase",phase(s,now));v.put("startsAt",s.getStartsAt());v.put("endsAt",s.getEndsAt());
        v.put("canExtend",admin&&"CONTEST".equals(s.getMode())&&"PUBLISHED".equals(s.getStatus())&&s.getEndsAt()!=null&&now<s.getEndsAt());v.put("serverNow",now);v.put("total",total);v.put("revision",s.getRevision());v.put("accessType",s.getAccessType());
        v.put("languages",strings(s.getLanguagesJson()));v.put("publicCode",Boolean.TRUE.equals(s.getPublicCode()));
        var p=participant(s.getId(),uid);v.put("joined",p!=null);v.put("disqualified",p!=null&&Boolean.TRUE.equals(p.getDisqualified()));
        v.put("participants",participants.selectCount(new LambdaQueryWrapper<OjProblemSetParticipant>().eq(OjProblemSetParticipant::getProblemSetId,s.getId())));
        v.put("canSubmit","PUBLISHED".equals(s.getStatus())&&(!"CONTEST".equals(s.getMode())||"RUNNING".equals(phase(s,now))&&p!=null)&&(p==null||!Boolean.TRUE.equals(p.getDisqualified())));
        v.put("canJoin","PUBLISHED".equals(s.getStatus())&&(!"CONTEST".equals(s.getMode())||"UPCOMING".equals(phase(s,now))));
        v.put("acCount",score(s,list,uid).acCount());
        if(admin)v.put("students",strings(s.getStudentsJson()));
        return v;
    }
    @Transactional(readOnly=true) public Map<String,Object> list(Long uid,boolean admin,String keyword,String mode,int page){
        User u=admin?null:user(uid);
        List<OjProblemSet> all=sets.selectList(new LambdaQueryWrapper<OjProblemSet>()
            .in(!admin,OjProblemSet::getStatus,List.of("PUBLISHED","ARCHIVED"))
            .eq(mode!=null&&!mode.isBlank(),OjProblemSet::getMode,mode).orderByDesc(OjProblemSet::getId));
        List<OjProblemSet> visible=all.stream().filter(s->admin||allowed(s,u)).filter(s->keyword==null||keyword.isBlank()||s.getName().toLowerCase().contains(keyword.toLowerCase())).toList();
        int start=(int)Math.min(visible.size(),(long)(Math.max(1,page)-1)*20),end=Math.min(visible.size(),start+20);
        return Map.of("records",visible.subList(start,end).stream().map(s->view(s,uid,admin)).toList(),"total",visible.size(),"page",Math.max(1,page));
    }
    @Transactional(readOnly=true) public Map<String,Object> detail(Long id,Long uid,boolean admin){
        OjProblemSet s=readable(id,uid,admin);var v=view(s,uid,admin);
        List<Map<String,Object>> rows=new ArrayList<>();int pos=0;
        if(admin||questionsVisible(s,System.currentTimeMillis()))for(var i:metadata(id)){
            Map<String,Object> r=new LinkedHashMap<>();r.put("id",i.getId());r.put("problemId",i.getProblemId());r.put("title",i.getTitle());
            r.put("difficulty",i.getDifficulty());r.put("sourcePlatform",i.getSourcePlatform());r.put("tags",strings(i.getTagsJson()));r.put("modes",strings(i.getModesJson()));
            r.put("revision",i.getRevision());r.put("active",i.getActive());r.put("cancelReason",i.getCancelReason());r.put("label",label(pos++));rows.add(r);
        }
        v.put("items",rows);
        if(admin)v.put("audit",audits.selectList(new LambdaQueryWrapper<OjProblemSetAudit>().eq(OjProblemSetAudit::getProblemSetId,id).orderByDesc(OjProblemSetAudit::getId).last("LIMIT 30")));
        return v;
    }
    static String label(int pos){StringBuilder s=new StringBuilder();for(int n=pos+1;n>0;n=(n-1)/26)s.insert(0,(char)('A'+(n-1)%26));return s.toString();}
    @Transactional public synchronized void join(Long id,Long uid){
        OjProblemSet s=lock(id);if(!"PUBLISHED".equals(s.getStatus())||!allowed(s,user(uid)))throw new BusinessException(403,"无法加入此题单");
        if(participant(id,uid)!=null)return;
        if("CONTEST".equals(s.getMode())&&!"UPCOMING".equals(phase(s,System.currentTimeMillis())))throw new BusinessException(400,"比赛报名已结束");
        var p=new OjProblemSetParticipant();p.setProblemSetId(id);p.setUserId(uid);p.setJoinedAt(System.currentTimeMillis());p.setDisqualified(false);participants.insert(p);rankings.remove(id);
    }
    private record Frozen(ProblemPack pack,List<OjLanguage> languages,String signature,String runtimeId){}
    private Frozen frozen(OjProblemSetItem i){
        Map<?,?> snap=oj.read(i.getSnapshotJson(),Map.class);ProblemPack p=oj.read(oj.write(snap.get("pack")),ProblemPack.class);
        List<OjLanguage> ls=new ArrayList<>();for(Object row:(List<?>)snap.get("languages"))ls.add(oj.read(oj.write(row),OjLanguage.class));
        return new Frozen(p,ls,(String)snap.get("signature"),(String)snap.get("runtimeId"));
    }
    public Map<String,Object> problem(Long id,Long itemId,Long uid){
        OjProblemSet s=readable(id,uid,false);if(!questionsVisible(s,System.currentTimeMillis()))throw new BusinessException(403,"比赛尚未开始，题目暂不可见");
        var i=item(id,itemId);if(!Boolean.TRUE.equals(i.getActive()))throw new BusinessException(404,"题目已从题单作废");var f=frozen(i);
        OjProblem p=new OjProblem();p.setId(i.getProblemId());p.setTitle(i.getTitle());p.setRevision(i.getRevision());p.setDifficulty(i.getDifficulty());p.setSlug(f.pack().getSlug());p.setStatus("CONTEXT");
        var result=oj.publicDetail(p,f.pack(),f.languages());result.put("problemSetId",id);result.put("problemSetItemId",i.getId());result.put("problemSet",view(s,uid,false));return result;
    }
    @Transactional public synchronized Map<String,Object> enqueue(Long id,Long itemId,Long uid,SubmitRequest r,String kind){
        OjProblemSet s=lock(id);readable(id,uid,false);var i=item(id,itemId);
        if(!Objects.equals(i.getProblemId(),r.getProblemId()))fail("题目与题单不匹配");
        Map<String,Object> replay=oj.replay(uid,id,itemId,r,kind);if(replay!=null)return replay;
        long now=System.currentTimeMillis();
        if(!"PUBLISHED".equals(s.getStatus()))fail("题单已归档，无法提交");
        if("CONTEST".equals(s.getMode())&&!"RUNNING".equals(phase(s,now)))fail(now<s.getStartsAt()?"比赛尚未开始":"比赛已结束，无法运行或提交");
        var p=participant(id,uid);if(p==null){if("CONTEST".equals(s.getMode()))throw new BusinessException(403,"请在开赛前报名");join(id,uid);p=participant(id,uid);}
        if(Boolean.TRUE.equals(p.getDisqualified()))throw new BusinessException(403,"当前账号已取消本题单参与资格");
        if(!Boolean.TRUE.equals(i.getActive()))fail("题目已作废");
        var f=frozen(i);if(!Objects.equals(f.runtimeId(),config.getRuntimeId()))throw new BusinessException(503,"题单运行环境已变更，请联系管理员重新验证");
        if(!f.pack().getModes().contains(r.getMode()))fail("题单不支持此做题模式");
        OjLanguage language=f.languages().stream().filter(l->l.getLanguageKey().equals(r.getLanguage())).findFirst().orElseThrow(()->new BusinessException(400,"题单不支持此语言"));
        // Recheck immediately before the durable insert; resource/DB waiting cannot bypass the deadline.
        long acceptedAt=System.currentTimeMillis();if("CONTEST".equals(s.getMode())&&acceptedAt>=s.getEndsAt())fail("比赛已结束，本次提交未接收");
        OjProblem problem=new OjProblem();problem.setId(i.getProblemId());problem.setRevision(i.getRevision());
        return oj.enqueueFrozen(uid,id,itemId,problem,f.pack(),language,f.signature(),r,kind,acceptedAt,"CONTEST".equals(s.getMode())?s.getEndsAt():null);
    }
    private String snapshot(OjProblem p,List<String> modes,List<String> languageKeys){
        if(!oj.currentSignature().equals(p.getValidationSignature()))fail("题目「"+p.getTitle()+"」尚未通过当前五语言验证");
        var pack=oj.read(p.getPackJson(),ProblemPack.class);
        if(modes.isEmpty()||!pack.getModes().containsAll(modes)||new HashSet<>(modes).size()!=modes.size())fail("题目模式无效");
        pack.setModes(modes);if(!modes.contains(pack.getDefaultMode()))pack.setDefaultMode(modes.getFirst());
        List<OjLanguage> languages=oj.languageList(false).stream().filter(l->languageKeys.contains(l.getLanguageKey())).toList();
        if(languages.size()!=languageKeys.size())fail("存在已停用或未知语言");
        return oj.write(Map.of("pack",pack,"languages",languages,"signature",p.getValidationSignature(),"runtimeId",config.getRuntimeId()));
    }
    private void validate(ProblemSetRequest r){
        if(r==null||r.getName()==null||r.getName().isBlank()||r.getName().length()>120)fail("题单名称须为 1–120 个字符");
        if(r.getDescription()==null||r.getDescription().length()>3000)fail("题单说明不能超过 3000 字符");
        if(r.getMode()==null||!List.of("PRACTICE","CONTEST").contains(r.getMode()))fail("题单模式无效");
        if(r.getAccessType()==null||!List.of("PUBLIC","WHITELIST").contains(r.getAccessType()))fail("参与范围无效");
        if(r.getStudents()==null||r.getStudents().size()>2000||r.getStudents().stream().anyMatch(v->v==null||!v.matches("[A-Za-z0-9_-]{1,20}")))fail("学号名单无效");
        if("WHITELIST".equals(r.getAccessType())&&r.getStudents().isEmpty())fail("指定名单不能为空");
        if(r.getLanguages()==null||r.getLanguages().isEmpty()||r.getLanguages().stream().anyMatch(Objects::isNull)||!List.of("c","cpp","java","python","rust").containsAll(r.getLanguages())||new HashSet<>(r.getLanguages()).size()!=r.getLanguages().size())fail("允许语言无效");
        if(r.getItems()==null||r.getItems().isEmpty()||r.getItems().size()>100)fail("题单须包含 1–100 道题目");
        Set<Long> ids=new HashSet<>();for(var i:r.getItems())if(i==null||i.getProblemId()==null||!ids.add(i.getProblemId())||i.getModes()==null||i.getModes().isEmpty()||i.getModes().stream().anyMatch(Objects::isNull))fail("题目重复或模式未选择");
        if("CONTEST".equals(r.getMode())&&(r.getStartsAt()!=null||r.getEndsAt()!=null)&&(r.getStartsAt()==null||r.getEndsAt()==null||r.getStartsAt()<0||r.getEndsAt()<=r.getStartsAt()))fail("比赛开始和结束时间无效");
    }
    @Transactional public synchronized Map<String,Object> save(Long id,ProblemSetRequest r,Long actor){
        validate(r);OjProblemSet s=id==null?new OjProblemSet():lock(id);long now=System.currentTimeMillis();
        if(id!=null&&!Objects.equals(s.getRevision(),r.getRevision()))throw new BusinessException(409,"题单已更新，请重新加载");
        List<OjProblemSetItem> previous=id==null?List.of():items.selectList(new LambdaQueryWrapper<OjProblemSetItem>().eq(OjProblemSetItem::getProblemSetId,id).orderByAsc(OjProblemSetItem::getSortOrder));
        boolean locked=id!=null&&"CONTEST".equals(s.getMode())&&s.getStartsAt()!=null&&now>=s.getStartsAt()&&!"DRAFT".equals(s.getStatus());
        boolean records=id!=null&&submissions.selectCount(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getProblemSetId,id))>0;
        if(records&&!Objects.equals(s.getMode(),r.getMode()))fail("已有提交的题单不能更换模式，请复制题单");
        if(id!=null&&"PUBLISHED".equals(s.getStatus())&&!locked&&"CONTEST".equals(r.getMode())&&(r.getStartsAt()==null||r.getEndsAt()==null||r.getStartsAt()<=now))fail("已发布比赛的开赛时间须在未来");
        if(locked && (!Objects.equals(s.getMode(),r.getMode())||!Objects.equals(s.getStartsAt(),r.getStartsAt())||!Objects.equals(s.getEndsAt(),r.getEndsAt())
            ||!Objects.equals(strings(s.getLanguagesJson()),r.getLanguages())||!Objects.equals(s.getAccessType(),r.getAccessType())||!Objects.equals(strings(s.getStudentsJson()),r.getStudents())
            ||!previous.stream().filter(i->Boolean.TRUE.equals(i.getActive())).map(OjProblemSetItem::getProblemId).toList().equals(r.getItems().stream().map(ProblemSetRequest.Item::getProblemId).toList())
            ||r.getItems().stream().anyMatch(v->previous.stream().filter(i->Objects.equals(i.getProblemId(),v.getProblemId())).anyMatch(i->!strings(i.getModesJson()).equals(v.getModes())))))fail("比赛已开赛，题目、时间、参与范围和计分配置已锁定");
        boolean languagesChanged=id!=null&&!strings(s.getLanguagesJson()).equals(r.getLanguages());
        s.setName(r.getName().trim());s.setDescription(r.getDescription());s.setMode(r.getMode());s.setAccessType(r.getAccessType());s.setStudentsJson(oj.write(r.getStudents()));s.setLanguagesJson(oj.write(r.getLanguages()));
        s.setStartsAt("CONTEST".equals(r.getMode())?r.getStartsAt():null);s.setEndsAt("CONTEST".equals(r.getMode())?r.getEndsAt():null);
        s.setUpdatedAt(now);s.setRevision(id==null?1:s.getRevision()+1);
        if(id==null){s.setCreatedAt(now);s.setStatus("DRAFT");s.setPublicCode(false);sets.insert(s);}else sets.update(null,new LambdaUpdateWrapper<OjProblemSet>().eq(OjProblemSet::getId,id)
            .set(OjProblemSet::getName,s.getName()).set(OjProblemSet::getDescription,s.getDescription()).set(OjProblemSet::getMode,s.getMode())
            .set(OjProblemSet::getAccessType,s.getAccessType()).set(OjProblemSet::getStudentsJson,s.getStudentsJson()).set(OjProblemSet::getLanguagesJson,s.getLanguagesJson())
            .set(OjProblemSet::getStartsAt,s.getStartsAt()).set(OjProblemSet::getEndsAt,s.getEndsAt()).set(OjProblemSet::getUpdatedAt,s.getUpdatedAt()).set(OjProblemSet::getRevision,s.getRevision()));
        if(locked){audit(s.getId(),actor,"EDIT_METADATA",null,"");return detail(s.getId(),actor,true);}
        Map<Long,OjProblemSetItem> old=new HashMap<>();previous.forEach(i->old.put(i.getProblemId(),i));Set<Long> requested=new HashSet<>();int position=0;long bytes=0;
        for(var requestedItem:r.getItems()){
            OjProblem p=oj.requireProblem(requestedItem.getProblemId());
            if(!List.of("PUBLISHED","CONTEST_ONLY").contains(p.getStatus()))fail("只能选择已发布或仅管理端可见的已验证题目");
            if("PRACTICE".equals(s.getMode())&&!"PUBLISHED".equals(p.getStatus()))fail("仅管理端可见的题目只能用于比赛题单");
            var i=old.get(p.getId());boolean fresh=i==null;if(fresh){i=new OjProblemSetItem();i.setProblemSetId(s.getId());i.setProblemId(p.getId());}
            boolean keep=!fresh&&"PUBLISHED".equals(s.getStatus())&&!languagesChanged&&strings(i.getModesJson()).equals(requestedItem.getModes());
            if(!keep){String snap=snapshot(p,requestedItem.getModes(),r.getLanguages());i.setSnapshotJson(snap);i.setRevision(p.getRevision());i.setValidationSignature(p.getValidationSignature());i.setTitle(p.getTitle());i.setDifficulty(p.getDifficulty());
                var pack=oj.read(p.getPackJson(),ProblemPack.class);i.setSourcePlatform(pack.getSourcePlatform());i.setTagsJson(oj.write(pack.getTags()));i.setModesJson(oj.write(requestedItem.getModes()));}
            bytes+=i.getSnapshotJson().length();if(bytes>16_000_000)fail("题单测试数据总量超过 16 MB，请减少题目数量");
            i.setSortOrder(position++);i.setActive(true);i.setCancelReason("");if(fresh)items.insert(i);else items.updateById(i);requested.add(p.getId());
        }
        for(var i:previous)if(!requested.contains(i.getProblemId()))items.update(null,new LambdaUpdateWrapper<OjProblemSetItem>().eq(OjProblemSetItem::getId,i.getId()).set(OjProblemSetItem::getActive,false).set(OjProblemSetItem::getCancelReason,"已从题单移除"));
        audit(s.getId(),actor,id==null?"CREATE":"EDIT",null,"");return detail(s.getId(),actor,true);
    }
    @Transactional public synchronized void status(Long id,String status,Long actor){
        var s=lock(id);if(status==null||!List.of("PUBLISHED","ARCHIVED").contains(status))fail("题单状态无效");
        if("ARCHIVED".equals(s.getStatus())&&"PUBLISHED".equals(status)&&"CONTEST".equals(s.getMode()))fail("已归档的比赛不能重新发布，请复制题单");
        if("PUBLISHED".equals(status)){
            if("CONTEST".equals(s.getMode())&&(s.getStartsAt()==null||s.getEndsAt()==null||s.getEndsAt()<=System.currentTimeMillis()))fail("请先设置有效比赛时间");
            if("CONTEST".equals(s.getMode())&&"DRAFT".equals(s.getStatus())&&s.getStartsAt()<=System.currentTimeMillis())fail("请将开赛时间设置在未来，以便报名");
            var rows=items.selectList(new LambdaQueryWrapper<OjProblemSetItem>().eq(OjProblemSetItem::getProblemSetId,id).eq(OjProblemSetItem::getActive,true));if(rows.isEmpty())fail("题单没有有效题目");
            for(var i:rows){var p=oj.requireProblem(i.getProblemId());var f=frozen(i);
                if(!Objects.equals(p.getRevision(),i.getRevision())||!Objects.equals(p.getValidationSignature(),oj.currentSignature())||!Objects.equals(f.signature(),p.getValidationSignature())||!Objects.equals(f.runtimeId(),config.getRuntimeId()))fail("题目或运行配置已变化，请重新保存题单快照");
                if(!List.of("PUBLISHED","CONTEST_ONLY").contains(p.getStatus())||"PRACTICE".equals(s.getMode())&&!"PUBLISHED".equals(p.getStatus()))fail("题目发布状态不符合题单模式");
            }
        }
        sets.update(null,new LambdaUpdateWrapper<OjProblemSet>().eq(OjProblemSet::getId,id).set(OjProblemSet::getStatus,status).set(OjProblemSet::getRevision,s.getRevision()+1).set(OjProblemSet::getUpdatedAt,System.currentTimeMillis()));audit(id,actor,status,null,"");
    }
    @Transactional public Map<String,Object> copy(Long id,Long actor){
        var original=require(id);var r=new ProblemSetRequest();r.setName(original.getName().substring(0,Math.min(110,original.getName().length()))+"（副本）");r.setDescription(original.getDescription());r.setMode(original.getMode());
        r.setAccessType(original.getAccessType());r.setStudents(strings(original.getStudentsJson()));r.setLanguages(strings(original.getLanguagesJson()));
        for(var i:metadata(id))if(Boolean.TRUE.equals(i.getActive())){var value=new ProblemSetRequest.Item();value.setProblemId(i.getProblemId());value.setModes(strings(i.getModesJson()));r.getItems().add(value);}
        return save(null,r,actor);
    }
    private ProblemSetScore score(OjProblemSet s,List<OjProblemSetItem> rows,Long uid){
        return summarized(s,rows,scoreCells(s,rows,uid).getOrDefault(uid,Map.of()));
    }
    private ProblemSetScore summarized(OjProblemSet s,List<OjProblemSetItem> rows,Map<Long,ProblemSetScore.Cell> scored){
        Map<Long,ProblemSetScore.Cell> cells=new LinkedHashMap<>();int ac=0;long penalty=0,last=0;
        for(var item:rows)if(Boolean.TRUE.equals(item.getActive())) {
            var cell=scored.getOrDefault(item.getId(),new ProblemSetScore.Cell(false,0,0,null,0));cells.put(item.getId(),cell);
            if(cell.accepted()){ac++;last=Math.max(last,cell.acceptedAt());if("CONTEST".equals(s.getMode()))penalty+=cell.elapsedMinutes()+20L*cell.wrong();}
        }
        return new ProblemSetScore(ac,penalty,last,cells);
    }
    /** SQL transfers one aggregate per user/problem instead of every historical submission. */
    private Map<Long,Map<Long,ProblemSetScore.Cell>> scoreCells(OjProblemSet s,List<OjProblemSetItem> rows,Long uid){
        List<Object> args=new ArrayList<>();args.add(s.getId());StringBuilder valid=new StringBuilder();
        for(var item:rows)if(Boolean.TRUE.equals(item.getActive())) {
            var modes=strings(item.getModesJson());if(modes.isEmpty())continue;
            if(!valid.isEmpty())valid.append(" OR ");
            valid.append("(j.problem_set_item_id=? AND j.revision=? AND j.validation_signature=? AND j.mode IN (")
                .append(String.join(",",Collections.nCopies(modes.size(),"?"))).append("))");
            args.add(item.getId());args.add(item.getRevision());args.add(item.getValidationSignature());args.addAll(modes);
        }
        if(valid.isEmpty())return Map.of();
        String where="j.problem_set_id=? AND j.kind='SUBMIT' AND j.accepted_at IS NOT NULL AND ("+valid+")";
        if(uid!=null){where+=" AND j.user_id=?";args.add(uid);}
        if("CONTEST".equals(s.getMode())){where+=" AND j.accepted_at>=? AND j.accepted_at<?";args.add(s.getStartsAt());args.add(s.getEndsAt());}
        String ordered="SELECT j.user_id,j.problem_set_item_id,j.status,j.accepted_at,ROW_NUMBER() OVER (PARTITION BY j.user_id,j.problem_set_item_id ORDER BY j.accepted_at,j.id) AS seq FROM oj_submission j WHERE "+where;
        String sql="SELECT user_id AS userId,problem_set_item_id AS itemId,MIN(CASE WHEN status='AC' THEN accepted_at END) AS acceptedAt,"+
            "SUM(CASE WHEN (first_ac IS NULL OR seq<first_ac) AND status IN ('WA','TLE','MLE','RE','OLE') THEN 1 ELSE 0 END) AS wrong,"+
            "SUM(CASE WHEN status IN ('QUEUED','RUNNING') THEN 1 ELSE 0 END) AS pending FROM (SELECT ordered.*,MIN(CASE WHEN status='AC' THEN seq END) OVER (PARTITION BY user_id,problem_set_item_id) AS first_ac FROM ("+ordered+") ordered) marked GROUP BY user_id,problem_set_item_id";
        Map<Long,Map<Long,ProblemSetScore.Cell>> result=new HashMap<>();
        for(var row:db.queryForList(sql,args.toArray())) {
            Long accepted=row.get("acceptedAt")==null?null:((Number)row.get("acceptedAt")).longValue();
            long elapsed=accepted==null||!"CONTEST".equals(s.getMode())?0:Math.max(0,(accepted-s.getStartsAt())/60000);
            var cell=new ProblemSetScore.Cell(accepted!=null,((Number)row.get("wrong")).intValue(),((Number)row.get("pending")).intValue(),accepted,elapsed);
            result.computeIfAbsent(((Number)row.get("userId")).longValue(),k->new HashMap<>()).put(((Number)row.get("itemId")).longValue(),cell);
        }
        return result;
    }
    private record Ranking(long revision,long activity,long expires,List<Map<String,Object>> rows,Map<String,Object> totals){}
    private final Map<Long,Ranking> rankings=new HashMap<>();
    @Transactional(readOnly=true) public synchronized Map<String,Object> standings(Long id,Long uid,boolean admin,int page,boolean completed){
        OjProblemSet s=readable(id,uid,admin);long now=System.currentTimeMillis(),version=oj.activityVersion();
        Ranking cached=rankings.get(id);
        if(cached==null||cached.revision()!=s.getRevision()||cached.activity()!=version||cached.expires()<now){
            cached=buildRanking(s,now,version);if(rankings.size()>=128)rankings.clear();rankings.put(id,cached);
        }
        List<Map<String,Object>> result=cached.rows();
        if(completed)result=result.stream().filter(r->r.get("completedAt")!=null&&!Boolean.TRUE.equals(r.get("disqualified"))).toList();
        int start=(int)Math.min(result.size(),(long)(Math.max(1,page)-1)*50),end=Math.min(result.size(),start+50);
        return Map.of("records",result.subList(start,end),"total",result.size(),"totals",cached.totals(),"serverNow",now);
    }
    private Ranking buildRanking(OjProblemSet s,long now,long version){
        var problemRows=metadata(s.getId());int total=(int)problemRows.stream().filter(i->Boolean.TRUE.equals(i.getActive())).count();
        var cells=scoreCells(s,problemRows,null);List<Map<String,Object>> result=new ArrayList<>();
        for(var p:db.queryForList("SELECT u.id AS userId,u.name,u.nickname,u.student_id AS studentId,p.disqualified FROM oj_problem_set_participant p JOIN sys_user u ON u.id=p.user_id AND u.status=1 AND u.deleted=0 WHERE p.problem_set_id=?",s.getId())) {
            Long userId=((Number)p.get("userId")).longValue();var sc=summarized(s,problemRows,cells.getOrDefault(userId,Map.of()));
            Map<String,Object> r=new LinkedHashMap<>();r.put("userId",userId);r.put("name",p.get("name"));r.put("nickname",p.get("nickname"));r.put("studentId",p.get("studentId"));r.put("acCount",sc.acCount());r.put("penalty",sc.penalty());
            r.put("lastAcAt",sc.lastAcAt());r.put("completedAt",total>0&&sc.acCount()==total?sc.lastAcAt():null);r.put("cells",sc.cells());r.put("disqualified",Boolean.TRUE.equals(p.get("disqualified"))||p.get("disqualified") instanceof Number n&&n.intValue()!=0);
            for(String difficulty:List.of("EASY","MEDIUM","HARD"))r.put(difficulty.toLowerCase()+"Ac",problemRows.stream().filter(i->difficulty.equals(i.getDifficulty())&&Boolean.TRUE.equals(i.getActive())&&sc.cells().get(i.getId()).accepted()).count());result.add(r);
        }
        result.sort(Comparator.<Map<String,Object>,Boolean>comparing(r->(Boolean)r.get("disqualified")).thenComparing(r->-((Number)r.get("acCount")).intValue())
            .thenComparingLong(r->((Number)r.get("penalty")).longValue()).thenComparingLong(r->{long t=((Number)r.get("lastAcAt")).longValue();return t==0?Long.MAX_VALUE:t;}).thenComparingLong(r->((Number)r.get("userId")).longValue()));
        Map<String,Object> prev=null;int rank=0,index=0;for(var r:result){index++;if(Boolean.TRUE.equals(r.get("disqualified"))){r.put("rank",null);continue;}
            if(prev==null||!Objects.equals(prev.get("acCount"),r.get("acCount"))||!Objects.equals(prev.get("penalty"),r.get("penalty"))||!Objects.equals(prev.get("lastAcAt"),r.get("lastAcAt")))rank=index;r.put("rank",rank);prev=r;}
        Map<String,Object> totals=new LinkedHashMap<>();totals.put("total",total);for(String d:List.of("EASY","MEDIUM","HARD"))totals.put(d.toLowerCase(),problemRows.stream().filter(i->Boolean.TRUE.equals(i.getActive())&&d.equals(i.getDifficulty())).count());
        return new Ranking(s.getRevision(),version,now+5000,List.copyOf(result),totals);
    }
    public Map<String,Object> history(Long id,Long uid,boolean admin,boolean mine,Long itemId,String language,String verdict,int page){
        OjProblemSet s=readable(id,uid,admin);if(!admin&&!questionsVisible(s,System.currentTimeMillis()))return Map.of("records",List.of(),"total",0);
        String where=" WHERE t.problem_set_id=? AND t.kind<>'VALIDATE'";List<Object> args=new ArrayList<>();args.add(id);
        if(!admin&&mine){where+=" AND t.user_id=?";args.add(uid);}if(itemId!=null){where+=" AND t.problem_set_item_id=?";args.add(itemId);}
        if(language!=null&&!language.isBlank()){where+=" AND t.language=?";args.add(language);}if(verdict!=null&&!verdict.isBlank()){where+=" AND t.status=?";args.add(verdict);}
        long total=db.queryForObject("SELECT COUNT(*) FROM oj_submission t"+where,Long.class,args.toArray());args.add((Math.max(1,page)-1L)*50);
        List<Map<String,Object>> rows=db.queryForList("SELECT t.id,t.user_id AS userId,u.name,u.nickname,u.student_id AS studentId,t.problem_set_item_id AS itemId,i.title AS problemTitle,t.kind,t.language,t.mode,t.status,t.accepted_at AS acceptedAt,t.passed_cases AS passedCases,t.total_cases AS totalCases FROM oj_submission t LEFT JOIN sys_user u ON u.id=t.user_id LEFT JOIN oj_problem_set_item i ON i.id=t.problem_set_item_id"+where+" ORDER BY t.id DESC LIMIT 50 OFFSET ?",args.toArray());
        // JDBC label casing varies between H2 and MySQL; keep the HTTP field names stable.
        List<Map<String,Object>> records=new ArrayList<>();
        for(var row:rows){var v=new LinkedHashMap<String,Object>();for(String key:List.of("id","userId","name","nickname","studentId","itemId","problemTitle","kind","language","mode","status","acceptedAt","passedCases","totalCases"))v.put(key,row.get(key));
            long owner=((Number)row.get("userId")).longValue();v.put("canViewCode",admin||owner==uid||"SUBMIT".equals(row.get("kind"))&&codePublic(s,uid));records.add(v);}
        return Map.of("records",records,"total",total);
    }
    private boolean codePublic(OjProblemSet s,Long uid){var p=participant(s.getId(),uid);return p!=null&&!Boolean.TRUE.equals(p.getDisqualified())&&("PRACTICE".equals(s.getMode())||Boolean.TRUE.equals(s.getPublicCode())&&s.getEndsAt()!=null&&System.currentTimeMillis()>=s.getEndsAt());}
    public Map<String,Object> source(Long setId,Long jobId,Long uid,boolean admin){
        OjProblemSet s=readable(setId,uid,admin);var job=submissions.selectById(jobId);
        if(job==null||!Objects.equals(job.getProblemSetId(),setId)||"VALIDATE".equals(job.getKind()))throw new BusinessException(404,"提交不存在");
        if(!admin&&!Objects.equals(job.getUserId(),uid)&&(!"SUBMIT".equals(job.getKind())||!codePublic(s,uid)))throw new BusinessException(403,"当前无权查看这次提交代码");
        return Map.of("id",job.getId(),"code",job.getCode(),"language",job.getLanguage(),"mode",job.getMode(),"status",job.getStatus());
    }
    @Transactional public synchronized void publicCode(Long id,boolean enabled,Long actor){var s=lock(id);if(!"CONTEST".equals(s.getMode())||s.getEndsAt()==null||System.currentTimeMillis()<s.getEndsAt())fail("只能在比赛结束后设置源码公开");sets.update(null,new LambdaUpdateWrapper<OjProblemSet>().eq(OjProblemSet::getId,id).set(OjProblemSet::getPublicCode,enabled));audit(id,actor,enabled?"OPEN_CODE":"CLOSE_CODE",null,"");}
    @Transactional public synchronized void cancel(Long id,Long itemId,String reason,Long actor){lock(id);var i=item(id,itemId);reason(reason);if(metadata(id).stream().filter(v->Boolean.TRUE.equals(v.getActive())).count()<=1)fail("不能作废最后一道题，请归档题单");items.update(null,new LambdaUpdateWrapper<OjProblemSetItem>().eq(OjProblemSetItem::getId,i.getId()).set(OjProblemSetItem::getActive,false).set(OjProblemSetItem::getCancelReason,reason));audit(id,actor,"CANCEL_ITEM",null,reason);}
    @Transactional public synchronized void extend(Long id,long endsAt,String reason,Long actor){var s=lock(id);reason(reason);if(!"CONTEST".equals(s.getMode())||!"PUBLISHED".equals(s.getStatus())||s.getEndsAt()==null||endsAt<=s.getEndsAt())fail("只能延长已发布比赛的结束时间");if(System.currentTimeMillis()>=s.getEndsAt()||Boolean.TRUE.equals(s.getPublicCode()))fail("已结束或公开过源码的比赛不能延长，请复制为新比赛");sets.update(null,new LambdaUpdateWrapper<OjProblemSet>().eq(OjProblemSet::getId,id).set(OjProblemSet::getEndsAt,endsAt).set(OjProblemSet::getRevision,s.getRevision()+1));audit(id,actor,"EXTEND",null,reason);}
    @Transactional public synchronized void disqualify(Long id,Long target,boolean value,String reason,Long actor){lock(id);reason(reason);var p=participant(id,target);if(p==null)fail("参与者不存在");participants.update(null,new LambdaUpdateWrapper<OjProblemSetParticipant>().eq(OjProblemSetParticipant::getId,p.getId()).set(OjProblemSetParticipant::getDisqualified,value).set(OjProblemSetParticipant::getReason,reason));audit(id,actor,value?"DISQUALIFY":"RESTORE",null,reason);}
    @Transactional public synchronized void rejudge(Long id,Long jobId,String reason,Long actor){lock(id);reason(reason);var job=submissions.selectById(jobId);if(job==null||!Objects.equals(job.getProblemSetId(),id)||!"SUBMIT".equals(job.getKind()))fail("只能重判本题单正式提交");oj.rejudge(job,reason);audit(id,actor,"REJUDGE",jobId,reason);}
    private void reason(String reason){if(reason==null||reason.isBlank()||reason.length()>500)fail("请填写 1–500 字符的操作原因");}
    private void audit(Long id,Long actor,String action,Long job,String reason){var a=new OjProblemSetAudit();a.setProblemSetId(id);a.setActorId(actor);a.setAction(action);a.setJobId(job);a.setReason(reason);a.setCreatedAt(System.currentTimeMillis());audits.insert(a);rankings.remove(id);}
}
