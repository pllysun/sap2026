package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.dto.judger.*;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OjSolutionTest {
    static {
        var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"");assistant.setCurrentNamespace("com.sap.test.solutions");
        for(Class<?> c:List.of(OjSolution.class,OjProblem.class,OjProblemSet.class,OjProblemSetItem.class))com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,c);
    }
    final OjSolutionMapper solutions=mock(OjSolutionMapper.class);
    final OjProblemMapper problems=mock(OjProblemMapper.class);
    final OjProblemSetMapper sets=mock(OjProblemSetMapper.class);
    final OjProblemSetItemMapper items=mock(OjProblemSetItemMapper.class);
    final OjService oj=mock(OjService.class);
    final OjProblemSetService problemSets=mock(OjProblemSetService.class);
    final OjSolutionService service=spy(new OjSolutionService(solutions,problems,sets,items,oj,problemSets));
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final OjProblem p=new OjProblem();final OjProblemSet contest=new OjProblemSet();final OjProblemSetItem item=new OjProblemSetItem();
    final ProblemPack pack=new ProblemPack();final ProblemSolutionDocument doc=doc();
    OjSolutionTest(){
        doReturn(2000L).when(service).now();
        p.setId(1L);p.setRevision(2L);p.setTitle("示例题");p.setSlug("example");p.setValidationSignature("valid");p.setStatus("PUBLISHED");
        pack.setSlug("example");pack.setTitle("示例题");pack.setModes(List.of("STDIO"));
        for(String lang:OjSolutionContent.LANGUAGES)pack.getReferences().put(lang,Map.of("STDIO",program(lang)));
        try{p.setPackJson(json.writeValueAsString(pack));}catch(Exception e){throw new RuntimeException(e);}
        when(oj.requireProblem(1L)).thenReturn(p);when(oj.currentSignature()).thenReturn("valid");when(problems.selectOne(any())).thenReturn(p);
        when(oj.read(anyString(),any())).thenAnswer(i->json.readValue((String)i.getArgument(0),(Class<?>)i.getArgument(1)));
        when(oj.write(any())).thenAnswer(i->json.writeValueAsString(i.getArgument(0)));
        when(items.selectList(any())).thenReturn(List.of());
        when(solutions.insert(any())).thenAnswer(i->{((OjSolution)i.getArgument(0)).setId(9L);return 1;});
        contest.setId(3L);contest.setMode("CONTEST");contest.setStatus("PUBLISHED");contest.setStartsAt(1000L);contest.setEndsAt(3000L);
        item.setId(4L);item.setProblemSetId(3L);item.setProblemId(1L);item.setRevision(2L);item.setActive(true);
        when(items.selectById(4L)).thenReturn(item);when(problemSets.require(3L)).thenReturn(contest);when(problemSets.problem(3L,4L,7L)).thenReturn(Map.of("modes",List.of("STDIO")));
    }
    static String program(String l){return switch(l){case "python"->"print(0)\n";case "java"->"class Main { public static void main(String[] args) { System.out.println(0); } }";case "rust"->"fn main() { println!(\"0\"); }";default->"int main() { return 0; }";};}
    static ProblemSolutionDocument doc(){
        var d=new ProblemSolutionDocument();d.setProblemSlug("example");d.setProblemRevision(2L);d.setTitle("示例题");d.setSummary("先计算，再输出。");
        for(int i=0;i<8;i++){var s=new ProblemSolutionDocument.Section();s.setKey(OjSolutionContent.KEYS.get(i));s.setTitle(OjSolutionContent.TITLES.get(i));s.setMarkdown("具体解释示例变量、过程和结果。");d.getSections().add(s);}
        Map<String,String> codes=new LinkedHashMap<>();for(String l:OjSolutionContent.LANGUAGES)codes.put(l,(l.equals("python")?"# 说明输出\n":"// 说明输出\n")+program(l));d.getCodes().put("STDIO",codes);return d;
    }
    void stored()throws Exception{var s=new OjSolution();s.setDocumentJson(json.writeValueAsString(doc));s.setUpdatedAt(LocalDateTime.now());when(solutions.selectOne(any())).thenReturn(s);}
    @Test void libraryUsesActiveAccountAndStatementVisibilityBeforeEditorial()throws Exception{
        stored();assertEquals("READY",service.library(1L,7L).get("state"));verify(oj).requireActiveAccount(7L);verify(oj).detail(1L,false);
        doThrow(new BusinessException(404,"不可见")).when(oj).detail(1L,false);assertThrows(BusinessException.class,()->service.library(1L,7L));
    }
    @Test void contestNeverSerializesAnyCodeOrExplanationBeforeEnd()throws Exception{
        stored();var r=service.inSet(3L,4L,7L);assertEquals("LOCKED",r.get("state"));assertFalse(r.containsKey("document"));assertFalse(r.toString().contains("说明输出"));verify(solutions,never()).selectOne(any());
    }
    @Test void exactDeadlineOpensEvenIfParticipantCodeRemainsPrivate()throws Exception{
        stored();contest.setEndsAt(2000L);contest.setPublicCode(false);assertEquals("READY",service.inSet(3L,4L,7L).get("state"));
    }
    @Test void earlyArchiveDoesNotRevealContestAnswers(){contest.setStatus("ARCHIVED");assertEquals("LOCKED",service.inSet(3L,4L,7L).get("state"));}
    @Test void setAclAndCancelledItemsAreNotBypassed(){when(problemSets.problem(3L,4L,7L)).thenThrow(new BusinessException(404,"无权访问"));assertThrows(BusinessException.class,()->service.inSet(3L,4L,7L));verify(solutions,never()).selectOne(any());}
    @Test void directLibraryAndPracticeLinksCannotBypassOtherActiveContest(){
        when(items.selectList(any())).thenReturn(List.of(item));when(sets.selectBatchIds(any())).thenReturn(List.of(contest));
        assertEquals("LOCKED",service.library(1L,7L).get("state"));
        var practice=new OjProblemSet();practice.setId(3L);practice.setMode("PRACTICE");when(problemSets.require(3L)).thenReturn(practice);
        assertEquals("LOCKED",service.inSet(3L,4L,7L).get("state"));verify(solutions,never()).selectOne(any());
    }
    @Test void frozenRevisionAndAllowedModesAreUsedInsteadOfNewestEditorial()throws Exception{
        stored();contest.setEndsAt(1000L);item.setRevision(1L);service.inSet(3L,4L,7L);
        var captor=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);verify(solutions).selectOne(captor.capture());
        var values=((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)captor.getValue());values.getSqlSegment();assertTrue(values.getParamNameValuePairs().containsValue(1L));
    }
    @Test void administratorCanReadPrivateDraftSolution()throws Exception{stored();p.setStatus("CONTEST_ONLY");assertEquals("READY",service.admin(1L).get("state"));verify(oj,never()).detail(anyLong(),eq(false));}
    @Test void saveDoesNotMutateProblemsTestPacksOrSubmissions(){
        var saved=service.save(1L,doc);assertEquals("READY",saved.get("state"));verify(solutions).insert(any());verify(problems,never()).updateById(any());assertEquals(2L,p.getRevision());
    }
    @Test void outdatedOrUnvalidatedProgramsCannotBePublished(){
        doc.setProblemRevision(1L);assertEquals(409,assertThrows(BusinessException.class,()->service.save(1L,doc)).getCode());
        doc.setProblemRevision(2L);p.setValidationSignature("old");assertThrows(BusinessException.class,()->service.save(1L,doc));verify(solutions,never()).insert(any());
    }
    @Test void batchRejectsDuplicatesAndOversizeLists(){assertThrows(BusinessException.class,()->service.importDocuments(Collections.nCopies(11,doc)));assertThrows(BusinessException.class,()->service.importDocuments(List.of(doc,doc)));}
    @Test void codeEqualityKeepsLiteralsOperatorsAndPythonSuites(){
        assertTrue(OjSolutionContent.equivalent("x = 1\nif x:\n    print('#//')\n","# 输入\nx=1\nif x:\n    # 处理\n    print('#//') # 输出\n","python"));
        assertFalse(OjSolutionContent.equivalent("if x:\n    print(x)\n","if x:\nprint(x)\n","python"));
        assertFalse(OjSolutionContent.equivalent("print('a b')","print('ab')","python"));
        assertTrue(OjSolutionContent.equivalent("n=int(input())\nprint(n*(n+1)//2)\n","# 输入\nn=int(input())\nprint(\n    # 公式\n    n*(n+1)//2\n)\n","python"));
        assertTrue(OjSolutionContent.equivalent("int main(){return 0;}","// 入口\nint main() { /* 返回 */ return 0; }","c"));
        assertFalse(OjSolutionContent.equivalent("a+ +b","a++b","cpp"));
        assertTrue(OjSolutionContent.equivalent("fn a<'a>(s: &'a str) { println!(\"//\"); }","// 输入\nfn a<'a>(s: &'a str) { println!(\"//\"); }","rust"));
    }
    @Test void fiveLanguagesEveryModeAndOrderedSectionsAreRequired(){
        doc.getCodes().get("STDIO").remove("rust");assertThrows(BusinessException.class,()->OjSolutionContent.validate(doc,pack));
        doc.getCodes().get("STDIO").put("rust","// 注释\n"+program("rust"));Collections.swap(doc.getSections(),0,1);assertThrows(BusinessException.class,()->OjSolutionContent.validate(doc,pack));
    }
    @Test void safeVectorDiagramsAcceptedAndActiveContentRejected(){
        OjSolutionContent.validateSvg("<svg viewBox=\"0 0 720 300\" xmlns=\"http://www.w3.org/2000/svg\"><rect x=\"10\" y=\"20\" width=\"80\" height=\"40\" fill=\"#3267b1\"/><text x=\"20\" y=\"40\">下一步</text></svg>");
        for(String fragment:List.of("<script>alert(1)</script>","<image href=\"https://example.com/x\"/>","<foreignObject/>","<rect onclick=\"alert(1)\"/>","<rect style=\"background:url(https://example.com)\"/>","<rect fill=\"url(https://example.com)\"/>"))assertThrows(BusinessException.class,()->OjSolutionContent.validateSvg("<svg viewBox=\"0 0 10 10\">"+fragment+"</svg>"));
        assertThrows(BusinessException.class,()->OjSolutionContent.validateSvg("<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg viewBox=\"0 0 10 10\">&x;</svg>"));
    }
}
