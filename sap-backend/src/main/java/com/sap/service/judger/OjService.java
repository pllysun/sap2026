package com.sap.service.judger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.*;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import com.sap.dto.judger.*;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import com.sap.vo.judger.JudgeResult;
import jakarta.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service @RequiredArgsConstructor @Slf4j
public class OjService {
    private final OjProblemMapper problems;
    private final OjLanguageMapper languages;
    private final OjSubmissionMapper submissions;
    private final ObjectMapper json;
    private final JudgeEngine engine;
    private final GoJudgeClient client;
    private final JudgerProperties config;
    private final OjRuntimeLogService runtimeLogs;
    private final JudgerNodes nodes;
    private final OjSnapshotService snapshots;
    private final com.sap.mapper.UserMapper users;
    @org.springframework.beans.factory.annotation.Autowired
    private OjExecutionEvents executionEvents;
    private final OjCatalogCache catalogCache = new OjCatalogCache(new ObjectMapper());
    private final Set<Long> ownedJobs = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong activity = new java.util.concurrent.atomic.AtomicLong();
    public long activityVersion() {return activity.get();}
    public void requireActiveAccount(Long userId) {
        var user=users.selectById(userId);
        if(user==null || !Objects.equals(user.getStatus(),1)) throw new BusinessException(403,"账号不可用");
    }
    private final AtomicBoolean busy = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    @PostConstruct public void initialize() {
        String[][] defaults = {{"c","C","GCC 15.3 / C23","1000","128"},
            {"cpp","C++","GCC 15.3 / C++23","1000","128"}, {"java","Java","OpenJDK 27","2000","256"},
            {"python","Python","CPython 3.14.7","2000","128"}, {"rust","Rust","Rust 1.98.1 / Edition 2024","1000","128"}};
        for (String[] item : defaults) if (languages.selectCount(new LambdaQueryWrapper<OjLanguage>()
            .eq(OjLanguage::getLanguageKey,item[0])) == 0) {
            OjLanguage language = new OjLanguage(); language.setLanguageKey(item[0]); language.setLabel(item[1]);
            language.setVersion(item[2]); language.setEnabled(true); language.setTimeLimitMs(Integer.parseInt(item[3]));
            language.setMemoryLimitMb(Integer.parseInt(item[4])); language.setUpdatedAt(LocalDateTime.now());
            languages.insert(language);
        }
        long position=0;
        for (OjProblem p : problems.orderedMetadata()) {
            if (p.getSortOrder()==null) problems.update(null,new LambdaUpdateWrapper<OjProblem>()
                .eq(OjProblem::getId,p.getId()).set(OjProblem::getSortOrder,position));
            position++;
        }
        // The deployment is deliberately one application instance. Durable jobs survive restarts.
        // Preserve historical passes, including when a previously accepted job is later rejudged.
        submissions.update(null,new LambdaUpdateWrapper<OjSubmission>()
            .eq(OjSubmission::getKind,"SUBMIT").eq(OjSubmission::getStatus,"AC")
            .and(q -> q.isNull(OjSubmission::getEverAccepted).or().eq(OjSubmission::getEverAccepted,false))
            .set(OjSubmission::getEverAccepted,true));
        List<OjSubmission> interrupted=submissions.selectList(new LambdaQueryWrapper<OjSubmission>().select(OjSubmission::getId).eq(OjSubmission::getStatus,"RUNNING"));
        int recovered=submissions.update(null,new LambdaUpdateWrapper<OjSubmission>().eq(OjSubmission::getStatus,"RUNNING")
            .set(OjSubmission::getStatus,"QUEUED"));
        runtimeLogs.event("STARTUP",null,"READY");
        if(recovered>0) for(OjSubmission job:interrupted) runtimeLogs.event("RECOVERED",job.getId(),"QUEUED");
    }
    @PreDestroy public void shutdown() {
        worker.shutdownNow();
        // Give interrupted workers time to release their remote lease before JVM exit.
        try { worker.awaitTermination(5,TimeUnit.SECONDS); }
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }

    public List<OjLanguage> languageList(boolean admin) {
        return languages.selectList(new LambdaQueryWrapper<OjLanguage>().eq(!admin,OjLanguage::getEnabled,true)
            .orderByAsc(OjLanguage::getId));
    }
    public Map<String,Object> health() {
        Map<String,Object> result = new LinkedHashMap<>(); result.put("enabled",config.isEnabled());
        result.put("runtimeId",config.getRuntimeId()); result.put("busy",running.get()>0);
        result.put("queued",submissions.selectCount(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getStatus,"QUEUED")));
        Map<String,Object> pool=nodes.overview();result.putAll(pool);result.remove("nodes");
        result.put("available",config.isEnabled() && ((Number)pool.get("capacity")).intValue()>0);
        result.put("queueCapacity",queueLimit());
        result.put("maxJudgeGroupMs",Math.max(60000L,config.getMaxJudgeGroupMs()));
        result.put("dailySubmissionLimit",Math.max(0,config.getDailySubmissionLimit()));
        return result;
    }
    @Transactional public void saveLanguages(List<OjLanguage> updates) {
        if (updates == null || updates.isEmpty() || updates.size()>5) fail("语言配置不能为空");
        Set<String> keys = new HashSet<>();
        for (OjLanguage update : updates) {
            if (!keys.add(update.getLanguageKey())) fail("语言配置重复");
            OjLanguage old = languages.selectOne(new LambdaQueryWrapper<OjLanguage>().eq(OjLanguage::getLanguageKey,update.getLanguageKey()));
            if (old == null || update.getEnabled()==null || update.getTimeLimitMs()==null || update.getMemoryLimitMb()==null)
                fail("语言配置不完整");
            if (update.getTimeLimitMs()<100 || update.getTimeLimitMs()>10000 || update.getMemoryLimitMb()<32 || update.getMemoryLimitMb()>384)
                fail("时间限制须为 100–10000 ms，内存须为 32–384 MiB");
            if ("java".equals(old.getLanguageKey()) && update.getMemoryLimitMb()<128) fail("Java 内存限制至少为 128 MiB");
            old.setEnabled(update.getEnabled()); old.setTimeLimitMs(update.getTimeLimitMs());
            old.setMemoryLimitMb(update.getMemoryLimitMb()); old.setUpdatedAt(LocalDateTime.now()); languages.updateById(old);
        }
        if (languageList(false).isEmpty()) fail("至少启用一种语言");
        catalogCache.invalidateAfterCommit();
    }
    private String signature() {
        StringBuilder value = new StringBuilder(config.getRuntimeId());
        for (OjLanguage language : languageList(false)) value.append('|').append(language.getLanguageKey()).append(':')
            .append(language.getTimeLimitMs()).append(':').append(language.getMemoryLimitMb());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private boolean visible(OjProblem p, String signature) {
        return "PUBLISHED".equals(p.getStatus()) && signature.equals(p.getValidationSignature());
    }
    public OjProblem requireProblem(Long id) {
        OjProblem p = problems.selectById(id); if (p==null) throw new BusinessException(404,"题目不存在"); return p;
    }
    private OjProblem requireVisible(Long id) {
        OjProblem p = requireProblem(id); if (!visible(p,signature())) throw new BusinessException(404,"题目尚未发布或已停用"); return p;
    }
    public Map<String,Object> list(boolean admin, String keyword, String difficulty, int page, int size) {
        return list(admin,keyword,difficulty,null,null,null,page,size);
    }
    public Map<String,Object> list(boolean admin, String keyword, String difficulty, String tag, String source, String mode, int page, int size) {
        page=Math.max(1,page); size=Math.min(50,Math.max(1,size));
        if(!admin)return catalog(keyword,difficulty,tag,source,mode,page,size);
        String signature=signature(); List<Map<String,Object>> result=new ArrayList<>();
        for (OjProblem p : orderedProblems()) {
            if (!admin && !visible(p,signature)) continue;
            ProblemPack pack=read(p.getPackJson(),ProblemPack.class);
            if (keyword!=null && !keyword.isBlank() && !(p.getTitle()+" "+pack.getTags()+" "+pack.getSourcePlatform()).toLowerCase().contains(keyword.toLowerCase())) continue;
            if (difficulty!=null && !difficulty.isBlank() && !difficulty.equals(p.getDifficulty())) continue;
            if (tag!=null && !tag.isBlank() && !pack.getTags().contains(tag)) continue;
            if (source!=null && !source.isBlank() && !source.equals(pack.getSourcePlatform())) continue;
            if (mode!=null && !mode.isBlank() && !pack.getModes().contains(mode)) continue;
            Map<String,Object> item=summary(p,pack);
            if (admin) { item.put("validated",signature.equals(p.getValidationSignature())); item.put("testCount",pack.getCases().size()); }
            result.add(item);
        }
        int start=(int)Math.min(result.size(),(long)(page-1)*size), end=Math.min(result.size(),start+size);
        return Map.of("records",result.subList(start,end),"total",result.size(),"page",page,"size",size);
    }
    public Map<String,Object> filters() {
        String signature=catalogSignature();
        return catalogCache.get("filters:"+signature,Map.class,()->{
            Set<String> tags=new TreeSet<>(),sources=new TreeSet<>(),modes=new TreeSet<>();
            for(var row:problems.catalogFilters(signature)) {
                tags.addAll(jsonStrings(row.getTagsJson()));modes.addAll(jsonStrings(row.getModesJson()));
                if(row.getSourcePlatform()!=null)sources.add(row.getSourcePlatform());
            }
            return Map.of("tags",tags,"sources",sources,"modes",modes);
        });
    }
    private String catalogSignature(){return catalogCache.get("signature:"+config.getRuntimeId(),String.class,this::signature);}
    public List<Long> visibleCatalogIds(List<Long> ids){return problems.visibleIds(catalogSignature(),ids);}
    private static String filter(String value){return value==null||value.isBlank()?null:value;}
    private List<String> jsonStrings(String value) {
        if(value==null)return List.of();
        try{return json.readValue(value,json.getTypeFactory().constructCollectionType(List.class,String.class));}
        catch(Exception e){throw new IllegalStateException("题库摘要格式无效",e);}
    }
    private Map<String,Object> catalog(String keyword,String difficulty,String tag,String source,String mode,int page,int size) {
        String signature=catalogSignature(),search=filter(keyword);
        if(search!=null)search=search.toLowerCase(Locale.ROOT);
        String level=filter(difficulty),tagJson=filter(tag)==null?null:write(tag),
            sourceJson=filter(source)==null?null:write(source),modeJson=filter(mode)==null?null:write(mode);
        if(level!=null&&!Set.of("EASY","MEDIUM","HARD").contains(level))
            return Map.of("records",List.of(),"total",0,"page",page,"size",size);
        String query=search;
        String key=write(Arrays.asList("catalog",signature,query,level,tagJson,sourceJson,modeJson,page,size));
        Map<String,Object> result=catalogCache.get(key,Map.class,()->{
            long total=problems.countCatalog(signature,query,level,tagJson,sourceJson,modeJson);
            long offset=(long)(page-1)*size;
            List<Map<String,Object>> rows=new ArrayList<>();
            if(offset<total)for(var p:problems.catalog(signature,query,level,tagJson,sourceJson,modeJson,size,offset)) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",p.getId());row.put("slug",p.getSlug());row.put("title",p.getTitle());
                row.put("difficulty",p.getDifficulty());row.put("status",p.getStatus());
                row.put("sortOrder",p.getSortOrder());row.put("revision",p.getRevision());
                row.put("tags",jsonStrings(p.getTagsJson()));row.put("modes",jsonStrings(p.getModesJson()));
                row.put("sourcePlatform",p.getSourcePlatform());rows.add(row);
            }
            return Map.of("records",rows,"total",Math.toIntExact(total),"page",page,"size",size);
        });
        // Generic JSON maps narrow small numbers to Integer. Preserve the existing
        // API's Long/string serialization contract and the progress overlay's IDs.
        for(var row:(List<Map<String,Object>>)result.get("records"))
            for(String field:List.of("id","revision","sortOrder"))
                if(row.get(field) instanceof Number value)row.put(field,value.longValue());
        return result;
    }
    public List<OjProblem> orderedProblems() {
        List<OjProblem> rows=problems.selectList(new LambdaQueryWrapper<OjProblem>());
        rows.sort(Comparator.comparing(OjProblem::getSortOrder,Comparator.nullsLast(Long::compareTo))
            .thenComparing(OjProblem::getId,Comparator.reverseOrder()));
        return rows;
    }
    public Map<String,Object> order() {
        return Map.of("records",problems.orderedMetadata().stream().map(p -> Map.of("id",p.getId(),"title",p.getTitle(),"status",p.getStatus())).toList());
    }
    @Transactional public synchronized void reorder(List<Long> expected, List<Long> ids) {
        List<OjProblem> locked=problems.selectList(new LambdaQueryWrapper<OjProblem>().last("FOR UPDATE"));
        locked.sort(Comparator.comparing(OjProblem::getSortOrder,Comparator.nullsLast(Long::compareTo)).thenComparing(OjProblem::getId,Comparator.reverseOrder()));
        List<Long> current=locked.stream().map(OjProblem::getId).toList();
        if (!current.equals(expected)) throw new BusinessException(409,"题目顺序已更新，请重新加载");
        if (ids==null || ids.size()!=current.size() || new HashSet<>(ids).size()!=ids.size() || !new HashSet<>(ids).equals(new HashSet<>(current))) fail("请提交完整且不重复的题目顺序");
        for (int i=0;i<ids.size();i++) problems.update(null,new LambdaUpdateWrapper<OjProblem>()
            .eq(OjProblem::getId,ids.get(i)).set(OjProblem::getSortOrder,(long)i));
        catalogCache.invalidateAfterCommit();
    }
    public String currentSignature() { return signature(); }
    private Map<String,Object> summary(OjProblem p, ProblemPack pack) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("sortOrder",p.getSortOrder()); result.put("id",p.getId()); result.put("title",p.getTitle()); result.put("slug",p.getSlug());
        result.put("difficulty",p.getDifficulty()); result.put("tags",pack.getTags()); result.put("modes",pack.getModes());
        result.put("sourcePlatform",pack.getSourcePlatform()); result.put("status",p.getStatus()); result.put("revision",p.getRevision());
        return result;
    }
    public Map<String,Object> detail(Long id, boolean admin) {
        OjProblem p=admin ? requireProblem(id) : requireVisible(id); ProblemPack pack=read(p.getPackJson(),ProblemPack.class);
        Map<String,Object> result=summary(p,pack);
        if (admin) { result.put("pack",pack); result.put("validationJobId",p.getValidationJobId());
            result.put("validated",signature().equals(p.getValidationSignature())); return result; }
        return publicDetail(p,pack,languageList(false));
    }
    public Map<String,Object> publicDetail(OjProblem p, ProblemPack pack, List<OjLanguage> allowed) {
        Map<String,Object> result=summary(p,pack);
        result.put("description",pack.getDescription()); result.put("inputFormat",pack.getInputFormat());
        result.put("outputFormat",pack.getOutputFormat()); result.put("constraints",pack.getConstraints());
        result.put("sourceUrl",pack.getSourceUrl()); result.put("sourceId",pack.getSourceId()); result.put("sourceNote",pack.getSourceNote());
        result.put("defaultMode",pack.getDefaultMode()); result.put("samples",pack.getCases().stream().filter(ProblemPack.TestCase::isSample).toList());
        Map<String,Object> templates=new LinkedHashMap<>();
        for (OjLanguage language : allowed) {
            ProblemPack.Profile profile=pack.getProfiles().get(language.getLanguageKey());
            Map<String,String> values=new LinkedHashMap<>();
            if (pack.getModes().contains("STDIO")) values.put("STDIO",profile.getStarterStdio());
            if (pack.getModes().contains("FUNCTION")) values.put("FUNCTION",profile.getStarterFunction());
            templates.put(language.getLanguageKey(),values);
        }
        result.put("templates",templates); result.put("languages",allowed); return result;
    }
    @Transactional public synchronized OjProblem save(Long id, ProblemPack pack, Long expectedRevision) {
        validatePack(pack);
        OjProblem p=id==null ? new OjProblem() : requireProblem(id);
        if (id!=null && !Objects.equals(p.getRevision(),expectedRevision)) throw new BusinessException(409,"题目已被更新，请重新加载后编辑");
        Long duplicates=problems.selectCount(new LambdaQueryWrapper<OjProblem>().eq(OjProblem::getSlug,pack.getSlug()).ne(id!=null,OjProblem::getId,id));
        if (duplicates>0) fail("题目标识已存在");
        p.setSlug(pack.getSlug()); p.setTitle(pack.getTitle()); p.setDifficulty(pack.getDifficulty());
        p.setPackJson(write(pack)); p.setStatus("DRAFT"); p.setRevision(id==null ? 1L : p.getRevision()+1);
        p.setValidationSignature(""); p.setValidationJobId(null); p.setUpdatedAt(LocalDateTime.now());
        if (id==null) { p.setCreatedAt(LocalDateTime.now()); p.setSortOrder(problems.orderedMetadata().stream().map(OjProblem::getSortOrder).filter(Objects::nonNull).max(Long::compareTo).orElse(-1L)+1); problems.insert(p); }
        else {
            int changed=problems.update(p,new LambdaUpdateWrapper<OjProblem>().eq(OjProblem::getId,id)
                .eq(OjProblem::getRevision,expectedRevision).set(OjProblem::getValidationJobId,null));
            if (changed==0) throw new BusinessException(409,"题目已被更新，请重新加载后编辑");
        }
        catalogCache.invalidateAfterCommit();
        return p;
    }
    private void validatePack(ProblemPack p) {
        if (p==null || p.getSlug()==null || !p.getSlug().matches("[a-z0-9][a-z0-9-]{0,99}")) fail("题目标识只能使用小写字母、数字和连字符");
        if (p.getTitle()==null || p.getTitle().isBlank() || p.getTitle().length()>200 || p.getDescription()==null || p.getDescription().isBlank()) fail("标题和题面不能为空");
        if (p.getDifficulty()==null || !List.of("EASY","MEDIUM","HARD").contains(p.getDifficulty())) fail("请选择题目难度");
        if (p.getSourcePlatform()==null || p.getSourcePlatform().isBlank() || p.getSourceNote()==null || p.getSourceNote().isBlank()) fail("必须注明题目来源和测试数据来源");
        if (!"ORIGINAL".equalsIgnoreCase(p.getSourcePlatform()) && !"原创".equals(p.getSourcePlatform())) {
            try { URI u=URI.create(p.getSourceUrl()); if (!"https".equals(u.getScheme()) || u.getHost()==null) fail("来源链接必须为 HTTPS 地址"); }
            catch (IllegalArgumentException | NullPointerException e) { fail("来源链接无效"); }
        }
        if (p.getModes()==null || p.getModes().isEmpty() || p.getModes().size()>2 || new HashSet<>(p.getModes()).size()!=p.getModes().size() ||
            p.getModes().stream().anyMatch(m -> m==null || !List.of("STDIO","FUNCTION").contains(m)) || !p.getModes().contains(p.getDefaultMode())) fail("做题模式或默认模式无效");
        if (p.getChecker()==null || !List.of("TOKENS","UNORDERED_TOKENS").contains(p.getChecker())) fail("比较方式无效");
        if (p.getCases()==null || p.getCases().isEmpty() || p.getCases().size()>500) fail("测试集须包含 1–500 个用例");
        long bytes=0; boolean sample=false;
        for (ProblemPack.TestCase test : p.getCases()) {
            if (test==null || test.getInput()==null || test.getExpectedOutput()==null || test.getName()==null || test.getName().isBlank()) fail("用例名称、输入和期望输出不能为空");
            bytes+=test.getInput().length()+test.getExpectedOutput().length(); sample|=test.isSample();
            if (test.getInput().length()>1048576 || test.getExpectedOutput().length()>262144) fail("单个用例过大");
        }
        if (!sample || bytes>2_000_000) fail("至少设置一个样例，测试数据总量不能超过 2 MB");
        if (p.getProfiles()==null || p.getReferences()==null || p.getTags()==null || p.getTags().size()>20) fail("语言模板、参考解或标签无效");
        for (String tag : p.getTags()) if (tag==null || tag.length()>50) fail("单个标签不能超过 50 个字符");
        for (OjLanguage language : languageList(false)) {
            String key=language.getLanguageKey(); ProblemPack.Profile profile=p.getProfiles().get(key);
            Map<String,String> references=p.getReferences().get(key);
            if (profile==null || references==null) fail("缺少 "+language.getLabel()+" 的模板或参考代码");
            for (String mode : p.getModes()) {
                String reference=references.get(mode), starter="STDIO".equals(mode) ? profile.getStarterStdio() : profile.getStarterFunction();
                if (reference==null || reference.isBlank() || reference.length()>65536 || starter==null || starter.isBlank() || starter.length()>65536) fail(key+" 的 "+mode+" 模板或参考代码无效");
            }
            if (p.getModes().contains("FUNCTION")) {
                String driver=profile.getFunctionDriver();
                if (driver==null || driver.length()>131072 || driver.indexOf("__USER_CODE__")<0 || driver.indexOf("__USER_CODE__")!=driver.lastIndexOf("__USER_CODE__")) fail(key+" 函数驱动必须包含唯一 __USER_CODE__ 标记");
            }
        }
        if (write(p).length()>4_000_000) fail("题目包过大");
    }
    @Transactional public void status(Long id, String status) {
        OjProblem p=requireProblem(id);
        if (status==null || !List.of("DRAFT","PUBLISHED","CONTEST_ONLY","DISABLED").contains(status)) fail("题目状态无效");
        if (List.of("PUBLISHED","CONTEST_ONLY").contains(status) && !signature().equals(p.getValidationSignature())) fail("当前题目和语言限制尚未通过五语言完整验证，请先验证");
        int changed=problems.update(null,new LambdaUpdateWrapper<OjProblem>().eq(OjProblem::getId,id)
            .eq(OjProblem::getRevision,p.getRevision()).eq(OjProblem::getValidationSignature,p.getValidationSignature())
            .set(OjProblem::getStatus,status).set(OjProblem::getUpdatedAt,LocalDateTime.now()));
        if (changed==0) throw new BusinessException(409,"题目已被更新，请重新加载");
        catalogCache.invalidateAfterCommit();
    }
    public synchronized Map<String,Object> enqueue(Long userId, SubmitRequest request, String kind) {
        requireActiveAccount(userId);
        if (!config.isEnabled()) throw new BusinessException(503,"判题服务尚未启用");
        OjProblem p=requireVisible(request.getProblemId()); ProblemPack pack=read(p.getPackJson(),ProblemPack.class);
        if (!pack.getModes().contains(request.getMode())) fail("该题不支持此做题模式");
        OjLanguage language=languageList(false).stream().filter(l -> l.getLanguageKey().equals(request.getLanguage())).findFirst().orElseThrow(() -> new BusinessException(400,"该语言已停用"));
        long pending=submissions.selectCount(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getUserId,userId).in(OjSubmission::getStatus,"QUEUED","RUNNING"));
        if (pending>=2) throw new BusinessException(429,"已有任务正在判题，请等待完成");
        return createJob(p,userId,kind,request.getLanguage(),request.getMode(),request.getCode(),
            Map.of("pack",pack,"languages",List.of(language),"signature",signature(),"custom",request.getInput()!=null,
                "input",request.getInput()==null ? "" : request.getInput()));
    }
    @Transactional public synchronized Map<String,Object> validate(Long id, Long userId) {
        if (!config.isEnabled()) throw new BusinessException(503,"判题服务尚未启用");
        OjProblem p=requireProblem(id); ProblemPack pack=read(p.getPackJson(),ProblemPack.class); validatePack(pack);
        if (submissions.selectCount(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getProblemId,id)
            .eq(OjSubmission::getKind,"VALIDATE").in(OjSubmission::getStatus,"QUEUED","RUNNING"))>0) fail("该题已有验证任务");
        Map<String,Object> job=createJob(p,userId,"VALIDATE","all","all","",Map.of("pack",pack,"languages",languageList(false),"signature",signature()));
        int changed=problems.update(null,new LambdaUpdateWrapper<OjProblem>().eq(OjProblem::getId,id)
            .eq(OjProblem::getRevision,p.getRevision()).set(OjProblem::getValidationJobId,job.get("id")));
        if (changed==0) throw new BusinessException(409,"题目已被更新，请重新加载后验证");
        return job;
    }
    private Map<String,Object> createJob(OjProblem problem, Long user, String kind, String language, String mode, String code, Object snapshot) {
        if(nodes.freeSlots()<=submissions.selectCount(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getStatus,"QUEUED"))) throw new BusinessException(503,"暂无可用判题资源：节点已停止、离线或正在满负载运行，请稍后重试");
        return insertJob(problem,user,kind,language,mode,code,snapshot,null,null,null,System.currentTimeMillis(),null);
    }
    public int queueLimit() { return Math.max(1,Math.min(5000,config.getQueueCapacity())); }
    /** The set service has already checked eligibility, time and the immutable snapshot. */
    public synchronized Map<String,Object> enqueueFrozen(Long user, Long setId, Long itemId, OjProblem problem,
        ProblemPack pack, OjLanguage language, String signature, SubmitRequest request, String kind, long acceptedAt, Long deadline) {
        requireActiveAccount(user);
        if(!config.isEnabled() || ((Number)nodes.overview().get("capacity")).intValue()==0)
            throw new BusinessException(503,"暂无在线判题节点，本次提交尚未接收");
        long pending=submissions.selectCount(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getUserId,user).in(OjSubmission::getStatus,"QUEUED","RUNNING"));
        if(pending>=2) throw new BusinessException(429,"已有任务正在判题，请等待完成");
        Map<String,Object> snapshot=Map.of("pack",pack,"languages",List.of(language),"signature",signature,
            "custom","RUN".equals(kind)&&request.getInput()!=null,"input","RUN".equals(kind)&&request.getInput()!=null?request.getInput():"");
        return insertJob(problem,user,kind,request.getLanguage(),request.getMode(),request.getCode(),snapshot,setId,itemId,request.getRequestKey(),acceptedAt,deadline);
    }
    public Map<String,Object> replay(Long user, Long setId, Long itemId, SubmitRequest request, String kind) {
        if(request.getRequestKey()==null || !request.getRequestKey().matches("[A-Za-z0-9_-]{16,64}")) fail("缺少有效的提交请求标识");
        OjSubmission old=submissions.selectOne(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getUserId,user).eq(OjSubmission::getRequestKey,request.getRequestKey()));
        if(old==null)return null;
        Map<?,?> snapshot=read(old.getSnapshotJson(),Map.class);
        boolean custom="RUN".equals(kind)&&request.getInput()!=null;
        if(!Objects.equals(old.getProblemSetId(),setId)||!Objects.equals(old.getProblemSetItemId(),itemId)
            ||!Objects.equals(old.getKind(),kind)||!Objects.equals(old.getLanguage(),request.getLanguage())
            ||!Objects.equals(old.getMode(),request.getMode())||!Objects.equals(old.getCode(),request.getCode())
            ||!Objects.equals(snapshot.get("custom"),custom)||!Objects.equals(snapshot.get("input"),custom?request.getInput():""))
            throw new BusinessException(409,"请求标识已用于不同的提交，请重新提交");
        return jobView(old,false,false);
    }
    private Map<String,Object> insertJob(OjProblem problem, Long user, String kind, String language, String mode, String code, Object snapshot,
        Long setId, Long itemId, String requestKey, long acceptedAt, Long deadline) {
        requireActiveAccount(user);
        if (config.getDailySubmissionLimit()>0 && !"VALIDATE".equals(kind) && submissions.selectCount(new LambdaQueryWrapper<OjSubmission>()
            .eq(OjSubmission::getUserId,user).ne(OjSubmission::getKind,"VALIDATE")
            .ge(OjSubmission::getCreatedAt,java.time.LocalDate.now().atStartOfDay()))>=Math.max(0,config.getDailySubmissionLimit()))
            throw new BusinessException(429,"已达到账号今日运行与提交上限，请明日再试");
        if (submissions.selectCount(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getStatus,"QUEUED"))>=queueLimit()) throw new BusinessException(429,"判题队列已满，本次提交尚未接收，请稍后重试");
        if(setId!=null)acceptedAt=System.currentTimeMillis();
        if(deadline!=null&&acceptedAt>=deadline)throw new BusinessException(400,"比赛已结束，本次提交未接收");
        OjSubmission job=new OjSubmission(); job.setProblemId(problem.getId()); job.setRevision(problem.getRevision());
        job.setProblemSetId(setId);job.setProblemSetItemId(itemId);job.setRequestKey(requestKey);job.setAcceptedAt(acceptedAt);job.setResultVersion(1);
        if(snapshot instanceof Map<?,?> m)job.setValidationSignature((String)m.get("signature"));
        job.setUserId(user); job.setKind(kind); job.setLanguage(language); job.setMode(mode); job.setCode(code);
        job.setStatus("QUEUED"); job.setSnapshotJson(snapshots.compact(snapshot)); job.setSnapshotKey(read(job.getSnapshotJson(),Map.class).get("packRef").toString()); job.setResultJson("{}"); job.setPassedCases(0); job.setTotalCases(0);
        job.setCreatedAt(LocalDateTime.now()); submissions.insert(job); activity.incrementAndGet(); runtimeLogs.event("QUEUED",job.getId(),job.getStatus()); stage(job,"QUEUED"); return jobView(job,false,false);
    }
    public Map<String,Object> job(Long id, Long user, boolean admin) {
        OjSubmission job=submissions.selectOne(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getId,id)
            .select(OjSubmission::getId,OjSubmission::getUserId,OjSubmission::getProblemId,OjSubmission::getRevision,
                OjSubmission::getKind,OjSubmission::getLanguage,OjSubmission::getMode,OjSubmission::getStatus,
                OjSubmission::getProblemSetId,OjSubmission::getProblemSetItemId,OjSubmission::getAcceptedAt,
                OjSubmission::getNodeId,OjSubmission::getNodeName,OjSubmission::getAttempt,OjSubmission::getCode,OjSubmission::getResultJson,OjSubmission::getPassedCases,
                OjSubmission::getTotalCases,OjSubmission::getCreatedAt));
        if (job==null || (!admin && (!Objects.equals(job.getUserId(),user) || "VALIDATE".equals(job.getKind())))) throw new BusinessException(404,"提交记录不存在");
        return jobView(job,true,admin);
    }
    public List<Map<String,Object>> history(Long problem, Long user, int page) {
        return submissions.selectList(new LambdaQueryWrapper<OjSubmission>()
            .select(OjSubmission::getId,OjSubmission::getProblemId,OjSubmission::getRevision,OjSubmission::getKind,
                OjSubmission::getLanguage,OjSubmission::getMode,OjSubmission::getStatus,OjSubmission::getCreatedAt,
                OjSubmission::getPassedCases,OjSubmission::getTotalCases)
            .eq(OjSubmission::getUserId,user)
            .isNull(OjSubmission::getProblemSetId)
            .eq(problem!=null,OjSubmission::getProblemId,problem).ne(OjSubmission::getKind,"VALIDATE")
            .orderByDesc(OjSubmission::getId).last("LIMIT 50 OFFSET "+((long)(Math.max(1,Math.min(100000,page))-1)*50))).stream().map(j -> jobView(j,false,false)).toList();
    }
    private Map<String,Object> jobView(OjSubmission job, boolean detail, boolean admin) {
        Map<String,Object> view=new LinkedHashMap<>(); view.put("id",job.getId()); view.put("problemId",job.getProblemId());
        view.put("revision",job.getRevision()); view.put("kind",job.getKind()); view.put("language",job.getLanguage());
        view.put("mode",job.getMode()); view.put("status",job.getStatus()); view.put("createdAt",job.getCreatedAt());
        view.put("problemSetId",job.getProblemSetId());view.put("problemSetItemId",job.getProblemSetItemId());view.put("acceptedAt",job.getAcceptedAt());
        view.put("nodeName",job.getNodeName());view.put("nodeId",job.getNodeId());view.put("attempt",job.getAttempt());
        view.put("passedCases",job.getPassedCases()); view.put("totalCases",job.getTotalCases());
        if (detail) { view.put("code",job.getCode()); view.put("result",read(job.getResultJson(),Map.class));
            if(executionEvents!=null && !"VALIDATE".equals(job.getKind()))view.put("progress",executionEvents.snapshot(job)); }
        return view;
    }
    public synchronized void rejudge(OjSubmission job,String reason) {
        if(Set.of("QUEUED","RUNNING").contains(job.getStatus()))throw new BusinessException(409,"任务尚未结束");
        if(!config.isEnabled()||((Number)nodes.overview().get("capacity")).intValue()==0)throw new BusinessException(503,"暂无在线判题节点");
        if(submissions.selectCount(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getStatus,"QUEUED"))>=queueLimit())throw new BusinessException(429,"判题队列已满");
        submissions.update(null,new LambdaUpdateWrapper<OjSubmission>().eq(OjSubmission::getId,job.getId()).eq(OjSubmission::getStatus,job.getStatus())
            .set(OjSubmission::getStatus,"QUEUED").set(OjSubmission::getStartedAt,null).set(OjSubmission::getFinishedAt,null)
            .set(OjSubmission::getResultJson,"{}").set(OjSubmission::getPassedCases,0).set(OjSubmission::getTotalCases,0)
            .set(OjSubmission::getResultVersion,job.getResultVersion()==null?2:job.getResultVersion()+1));
        if(executionEvents!=null)executionEvents.reset(job.getId());
        activity.incrementAndGet();runtimeLogs.event("REJUDGE",job.getId(),"QUEUED");
    }
    @Scheduled(fixedDelay=500) public void poll() {
        if (!config.isEnabled() || !busy.compareAndSet(false,true)) return;
        try {
            List<OjSubmission> jobs=submissions.selectList(new LambdaQueryWrapper<OjSubmission>().eq(OjSubmission::getStatus,"QUEUED")
                .orderByAsc(OjSubmission::getId).last("LIMIT 32"));
            for(OjSubmission job:jobs) {
                if(running.get()>=32)break;
                JudgerNodes.Lease lease=nodes.acquire(Set.of());
                if(lease==null) {
                    if(((Number)nodes.overview().get("capacity")).intValue()==0 && (job.getProblemSetId()==null || job.getAcceptedAt()==null || System.currentTimeMillis()-job.getAcceptedAt()>=config.getOfflineWaitMs())){job.setStatus("SYSTEM_ERROR");job.setResultJson(write(Map.of("message","暂无可用判题资源，请启动或接入节点后重试")));finish(job);}
                    break;
                }
                boolean dispatched=false;
                ownedJobs.add(job.getId());
                try {
                    int claimed=submissions.update(null,new LambdaUpdateWrapper<OjSubmission>().eq(OjSubmission::getId,job.getId())
                        .eq(OjSubmission::getStatus,"QUEUED").set(OjSubmission::getStatus,"RUNNING").set(OjSubmission::getStartedAt,LocalDateTime.now()));
                    if(claimed==0)continue;
                    running.incrementAndGet();
                    try {
                        worker.submit(()->{
                            try {runAssigned(job,lease);}
                            catch(Exception e){log.warn("OJ worker {} interrupted by {}",job.getId(),e.getClass().getSimpleName());}
                            finally {lease.close();running.decrementAndGet();ownedJobs.remove(job.getId());activity.incrementAndGet();}
                        });
                        dispatched=true;
                    }catch(RejectedExecutionException e){running.decrementAndGet();throw e;}
                } finally {
                    // Ownership transfers only after successful dispatch, even if
                    // the database claim or executor throws.
                    if(!dispatched){lease.close();ownedJobs.remove(job.getId());}
                }
            }
        }catch(Exception e){runtimeLogs.event("QUEUE_ERROR",null,"SYSTEM_ERROR");log.warn("OJ queue polling failed ({})",e.getClass().getSimpleName());}
        finally{busy.set(false);}
    }
    /** Recover only jobs with no live owner; never requeue a still executing task. */
    @Scheduled(initialDelay=5000,fixedDelay=5000) public void recoverOrphans() {
        if(!busy.compareAndSet(false,true))return;
        try {
            for(OjSubmission job:submissions.selectList(new LambdaQueryWrapper<OjSubmission>()
                .select(OjSubmission::getId).eq(OjSubmission::getStatus,"RUNNING").orderByAsc(OjSubmission::getId).last("LIMIT 32"))) {
                if(ownedJobs.contains(job.getId()))continue;
                if(submissions.update(null,new LambdaUpdateWrapper<OjSubmission>().eq(OjSubmission::getId,job.getId())
                    .eq(OjSubmission::getStatus,"RUNNING").set(OjSubmission::getStatus,"QUEUED")
                    .set(OjSubmission::getStartedAt,null).set(OjSubmission::getFinishedAt,null))>0) {
                    if(executionEvents!=null)executionEvents.reset(job.getId());
                    activity.incrementAndGet();runtimeLogs.event("RECOVERED",job.getId(),"QUEUED");
                }
            }
        }catch(Exception e){log.warn("OJ recovery deferred ({})",e.getClass().getSimpleName());}
        finally{busy.set(false);}
    }
    private void runAssigned(OjSubmission job,JudgerNodes.Lease first) {
        Set<Long> tried=new HashSet<>();JudgerNodes.Lease lease=first;
        while(lease!=null && tried.size()<3 && !Thread.currentThread().isInterrupted()) {
            try {
                tried.add(lease.node.getId());job.setNodeId(lease.node.getId());job.setNodeName(lease.node.getName());job.setAttempt(tried.size());
                job.setStatus("RUNNING");job.setPassedCases(0);job.setTotalCases(0);job.setResultJson("{}");updateJob(job);
                if(tried.size()>1)runtimeLogs.event("REASSIGNED",job.getId(),"RUNNING",job.getNodeName());
                runtimeLogs.event("STARTED",job.getId(),"RUNNING",job.getNodeName());
                stage(job,"DISPATCHED");
                lease.bind();process(job);return;
            }
            catch(NodeUnavailableException e){runtimeLogs.event("NODE_ERROR",job.getId(),"SYSTEM_ERROR",job.getNodeName());stage(job,"RETRYING");}
            finally{lease.close();}
            if(tried.size()>=3)break;
            lease=nodes.acquire(tried);
        }
        if(lease!=null)lease.close();
        if(Thread.currentThread().isInterrupted())return;
        job.setStatus("SYSTEM_ERROR");job.setResultJson(write(Map.of("message","判题节点连接中断，其他节点暂无可用资源，请稍后重试")));finish(job);
    }
    private void process(OjSubmission job) {
        try {
            JsonNode snapshot=snapshots.resolve(job.getSnapshotJson()); ProblemPack pack=json.treeToValue(snapshot.get("pack"),ProblemPack.class);
            List<OjLanguage> limits=new ArrayList<>(); for (JsonNode item : snapshot.get("languages")) limits.add(json.treeToValue(item,OjLanguage.class));
            if ("VALIDATE".equals(job.getKind())) {
                int total=pack.getCases().size()*3*limits.size()*pack.getModes().size(), passed=0;
                List<Map<String,Object>> reports=new ArrayList<>(); job.setTotalCases(total); job.setStatus("RUNNING"); updateJob(job);
                for (OjLanguage language : limits) for (String mode : pack.getModes()) {
                    int completed=passed;
                    JudgeResult report=engine.judge(pack,language,mode,pack.getReferences().get(language.getLanguageKey()).get(mode),pack.getCases(),false,3,
                        n -> { if (n%10==0) progress(job.getId(),completed+n,total); });
                    passed+=report.getPassedCases(); reports.add(Map.of("language",language.getLanguageKey(),"mode",mode,"result",report));
                    job.setPassedCases(passed); job.setResultJson(write(Map.of("reports",reports))); updateJob(job);
                    if (!"AC".equals(report.getVerdict())) { job.setStatus("VALIDATION_FAILED"); finish(job); return; }
                }
                job.setStatus("AC"); finish(job);
                // Publish authorization applies only to the exact tested revision and settings.
                problems.update(null,new LambdaUpdateWrapper<OjProblem>().eq(OjProblem::getId,job.getProblemId())
                    .eq(OjProblem::getRevision,job.getRevision()).eq(OjProblem::getValidationJobId,job.getId())
                    .set(OjProblem::getValidationSignature,snapshot.path("signature").asText()));
                catalogCache.invalidateAfterCommit();
            } else {
                List<ProblemPack.TestCase> tests=pack.getCases(); boolean reveal="RUN".equals(job.getKind());
                if (reveal && snapshot.path("custom").asBoolean()) {
                    ProblemPack.TestCase custom=new ProblemPack.TestCase(); custom.setName("自定义输入");
                    custom.setInput(snapshot.path("input").asText()); custom.setExpectedOutput(null); tests=List.of(custom);
                } else if (reveal) tests=tests.stream().filter(ProblemPack.TestCase::isSample).toList();
                job.setTotalCases(tests.size());
                JudgeResult result=engine.judge(pack,limits.get(0),job.getMode(),job.getCode(),tests,reveal,1,
                    n -> { if (n%10==0) progress(job.getId(),n,job.getTotalCases()); },
                    event -> { if(executionEvents!=null)executionEvents.publish(job,event); });
                job.setPassedCases(result.getPassedCases()); job.setResultJson(write(result)); job.setStatus(result.getVerdict()); finish(job);
            }
        } catch (NodeUnavailableException e) { throw e;
        } catch (Exception e) {
            log.warn("OJ job {} failed ({})",job.getId(),e.getClass().getSimpleName());
            job.setStatus("SYSTEM_ERROR"); job.setResultJson(write(Map.of("message","判题服务异常，请稍后重试"))); finish(job);
        }
    }
    private void progress(Long id,int passed,int total) {
        submissions.update(null,new LambdaUpdateWrapper<OjSubmission>().eq(OjSubmission::getId,id)
            .set(OjSubmission::getPassedCases,passed).set(OjSubmission::getTotalCases,total));
    }
    private void updateJob(OjSubmission job) {
        submissions.update(null,new LambdaUpdateWrapper<OjSubmission>().eq(OjSubmission::getId,job.getId())
            .set(OjSubmission::getStatus,job.getStatus()).set(OjSubmission::getResultJson,job.getResultJson())
            .set(OjSubmission::getNodeId,job.getNodeId()).set(OjSubmission::getNodeName,job.getNodeName()).set(OjSubmission::getAttempt,job.getAttempt())
            .set(OjSubmission::getPassedCases,job.getPassedCases()).set(OjSubmission::getTotalCases,job.getTotalCases())
            .set(Boolean.TRUE.equals(job.getEverAccepted()),OjSubmission::getEverAccepted,true)
            .set(OjSubmission::getFinishedAt,job.getFinishedAt()));
    }
    private void finish(OjSubmission job) {
        if ("SUBMIT".equals(job.getKind()) && "AC".equals(job.getStatus())) job.setEverAccepted(true);
        job.setFinishedAt(LocalDateTime.now()); updateJob(job);
        activity.incrementAndGet();runtimeLogs.event("FINISHED",job.getId(),job.getStatus(),job.getNodeName());
        stage(job,"FINISHED");
    }
    private void stage(OjSubmission job,String stage) {
        if(executionEvents!=null)executionEvents.publish(job,new com.sap.vo.judger.JudgeStage(stage,0,
            job.getPassedCases()==null?0:job.getPassedCases(),job.getPassedCases()==null?0:job.getPassedCases(),
            job.getTotalCases()==null?0:job.getTotalCases(),null));
    }
    public String write(Object value) {
        try { return json.writeValueAsString(value); } catch(Exception e) { throw new BusinessException(400,"数据格式无效"); }
    }
    public <T> T read(String value,Class<T> type) {
        try { return json.readValue(value,type); } catch(Exception e) { throw new BusinessException(400,"数据格式无效"); }
    }
    private static void fail(String message) { throw new BusinessException(400,message); }
}
