package com.sap.service.judger;

import com.fasterxml.jackson.databind.JsonNode;
import com.sap.common.BusinessException;
import com.sap.dto.judger.ProblemPack;
import com.sap.config.judger.JudgerProperties;
import com.sap.entity.judger.OjLanguage;
import com.sap.util.judger.OutputChecker;
import com.sap.vo.judger.JudgeResult;
import com.sap.vo.judger.JudgeStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Consumer;

@Service
public class JudgeEngine {
    private final GoJudgeClient client;
    private final JudgerProperties config;
    java.util.function.LongSupplier clock = System::nanoTime;
    public JudgeEngine(GoJudgeClient client) {this(client,new JudgerProperties());}
    @org.springframework.beans.factory.annotation.Autowired
    public JudgeEngine(GoJudgeClient client,JudgerProperties config) {this.client=client;this.config=config;}
    private static final List<String> JAVA_FLAGS = List.of("-Xss512k", "-XX:+UseSerialGC",
        "-XX:ActiveProcessorCount=1", "-XX:MaxMetaspaceSize=64m", "-XX:ReservedCodeCacheSize=24m",
        "-XX:CompressedClassSpaceSize=32m");

    public JudgeResult judge(ProblemPack pack, OjLanguage language, String mode, String userCode,
        List<ProblemPack.TestCase> tests, boolean reveal, int rounds, Consumer<Integer> progress) {
        return judge(pack,language,mode,userCode,tests,reveal,rounds,progress,stage -> {});
    }
    public JudgeResult judge(ProblemPack pack, OjLanguage language, String mode, String userCode,
        List<ProblemPack.TestCase> tests, boolean reveal, int rounds, Consumer<Integer> progress,
        Consumer<JudgeStage> stages) {
        JudgeResult result = new JudgeResult();
        JudgeOutputBudget outputBudget = new JudgeOutputBudget();
        JudgeOutputBudget nameBudget = new JudgeOutputBudget(JudgeOutputBudget.NAME_LIMIT);
        result.setTotalCases(tests.size() * rounds);
        String key = language.getLanguageKey();
        String source = userCode;
        if ("FUNCTION".equals(mode)) {
            String driver = pack.getProfiles().get(key).getFunctionDriver();
            source = driver.replace("__USER_CODE__", userCode);
        }
        String filename = switch (key) {
            case "c" -> "main.c"; case "cpp" -> "main.cpp"; case "java" -> "Main.java";
            case "python" -> "main.py"; case "rust" -> "main.rs";
            default -> throw new BusinessException(400,"不支持的语言");
        };
        Map<String,Object> inputs = Map.of(filename, Map.of("content",source));
        List<String> artifacts = switch(key) {
            case "python" -> List.of(); case "java" -> List.of("answer.jar"); default -> List.of("answer");
        };
        List<String> compileArgs = switch(key) {
            case "c" -> List.of("/usr/local/bin/gcc","-std=c23","-O2","-pipe",filename,"-o","answer","-lm");
            case "cpp" -> List.of("/usr/local/bin/g++","-std=c++23","-O2","-pipe",filename,"-o","answer");
            // LLD otherwise sizes its worker pool from host CPUs, exceeding the sandbox's
            // process limit on larger nodes even when a compilation has one CPU allocated.
            case "rust" -> List.of("/opt/rust/bin/rustc","--edition=2024","-C","opt-level=2",
                "-C","link-arg=-Wl,--threads=1",filename,"-o","answer");
            case "python" -> List.of("/usr/local/bin/python3","-c","import ast; ast.parse(open('main.py',encoding='utf-8').read(),filename='main.py')");
            // Fixed, trusted command; neither source nor user input is interpolated into the shell.
            case "java" -> List.of("/bin/sh","-c","/opt/java27/bin/javac -J-Xmx96m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=1 -encoding UTF-8 Main.java && /opt/java27/bin/jar c *.class > answer.jar");
            default -> throw new BusinessException(400,"不支持的语言");
        };
        List<String> ids = new ArrayList<>();
        long deadline = clock.getAsLong() + budgetMs(language,tests.size(),rounds) * 1_000_000L;
        try {
            stage(stages,"COMPILING",0,0,result,null);
            JsonNode compiled = client.execute(compileArgs,inputs,"",15000,512,true,artifacts);
            if("SYSTEM_ERROR".equals(verdict(compiled.path("status").asText())))throw new NodeUnavailableException();
            compiled.path("fileIds").fields().forEachRemaining(e -> ids.add(e.getValue().asText()));
            if (!"Accepted".equals(compiled.path("status").asText())) {
                result.setVerdict("CE"); result.setMessage("编译失败");
                result.setCompilerOutput(compiled.path("files").path("stderr").asText().substring(0,
                    Math.min(16000, compiled.path("files").path("stderr").asText().length())));
                stage(stages,"COMPILE_FAILED",0,0,result,"CE");
                return result;
            }
            if (!artifacts.isEmpty()) {
                Map<String,Object> binaries = new LinkedHashMap<>();
                for (String artifact : artifacts) {
                    String id = compiled.path("fileIds").path(artifact).asText();
                    if (id.isBlank()) throw new BusinessException(503,"编译产物缺失");
                    binaries.put(artifact,Map.of("fileId",id));
                }
                inputs = binaries;
            }
            stage(stages,"COMPILED",0,0,result,null);
            List<String> execute = new ArrayList<>();
            if ("python".equals(key)) execute.addAll(List.of("/usr/local/bin/python3","/w/main.py"));
            else if ("java".equals(key)) {
                execute.add("/opt/java27/bin/java"); execute.add("-Xmx"+javaHeapMb(language.getMemoryLimitMb())+"m"); execute.addAll(JAVA_FLAGS);
                execute.addAll(List.of("-cp","/w/answer.jar","Main"));
            } else execute.add("/w/answer");
            int completed=0;
            outer: for (int round=0; round<rounds; round++) for (ProblemPack.TestCase test : tests) {
                if (clock.getAsLong()>deadline || Thread.currentThread().isInterrupted()) {
                    result.setVerdict("SYSTEM_ERROR"); result.setMessage("判题任务达到服务保护时限，请联系管理员重判；本次不计比赛罚时"); break outer;
                }
                stage(stages,"RUNNING_CASE",completed+1,completed,result,null);
                JsonNode execution = client.execute(execute,inputs,test.getInput(),language.getTimeLimitMs(),
                    language.getMemoryLimitMb(),false,List.of());
                String verdict = verdict(execution.path("status").asText());
                if("SYSTEM_ERROR".equals(verdict))throw new NodeUnavailableException();
                String output = execution.path("files").path("stdout").asText();
                if ("AC".equals(verdict) && test.getExpectedOutput()!=null &&
                    !OutputChecker.matches(output,test.getExpectedOutput(),pack.getChecker())) verdict="WA";
                result.setTimeMs(Math.max(result.getTimeMs(), execution.path("time").asLong()/1_000_000));
                result.setMemoryBytes(Math.max(result.getMemoryBytes(),execution.path("memory").asLong()));
                if (reveal) {
                    Map<String,Object> view = new LinkedHashMap<>();
                    nameBudget.put(view,"name",test.getName()); view.put("verdict",verdict);
                    outputBudget.put(view,"output",output);
                    outputBudget.put(view,"stderr",execution.path("files").path("stderr").asText());
                    if (test.getExpectedOutput()!=null) outputBudget.put(view,"expected",test.getExpectedOutput());
                    result.setOutputTruncated(outputBudget.isTruncated() || nameBudget.isTruncated());
                    result.getCases().add(view);
                }
                if (!"AC".equals(verdict)) {
                    stage(stages,"CASE_FINISHED",++completed,completed,result,verdict);
                    result.setVerdict(verdict); result.setMessage(message(verdict)); break outer;
                }
                result.setPassedCases(result.getPassedCases()+1);
                stage(stages,"CASE_FINISHED",++completed,completed,result,verdict);
                progress.accept(result.getPassedCases());
            }
            return result;
        } finally {
            for (String id : ids) try { client.removeArtifact(id); } catch (Exception ignored) {}
        }
    }
    private void stage(Consumer<JudgeStage> listener,String stage,int index,int completed,JudgeResult result,String verdict) {
        try {listener.accept(new JudgeStage(stage,index,completed,result.getPassedCases(),result.getTotalCases(),verdict));}
        catch(RuntimeException ignored) { /* Progress delivery must never change a verdict. */ }
    }
    long budgetMs(OjLanguage language,int cases,int rounds) {
        long expected=45000L+(Math.max(3000L,language.getTimeLimitMs()*3L)+5000L)*cases*rounds;
        return Math.min(Math.max(60000L,config.getMaxJudgeGroupMs()),expected);
    }
    // Leave room for the VM, class metadata, code cache and threads inside the
    // same measured cgroup limit. At 256 MiB the former 96 MiB ceiling is 153 MiB.
    public static int javaHeapMb(int memoryMb) {return Math.max(16,Math.min(memoryMb-80,(int)(memoryMb*0.60)));}
    public static String verdict(String status) {
        return switch(status) {
            case "Accepted" -> "AC"; case "Time Limit Exceeded" -> "TLE";
            case "Memory Limit Exceeded" -> "MLE"; case "Output Limit Exceeded" -> "OLE";
            case "Nonzero Exit Status", "Signalled" -> "RE"; default -> "SYSTEM_ERROR";
        };
    }
    public static String message(String verdict) {
        return switch(verdict) {
            case "AC" -> "通过"; case "WA" -> "答案错误"; case "TLE" -> "运行超时";
            case "MLE" -> "内存超限"; case "OLE" -> "输出超限"; case "RE" -> "运行错误";
            case "CE" -> "编译失败"; default -> "判题服务异常，请稍后重试";
        };
    }
}
