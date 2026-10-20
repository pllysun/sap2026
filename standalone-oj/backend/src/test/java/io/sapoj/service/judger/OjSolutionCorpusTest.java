package io.sapoj.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapoj.dto.judger.ProblemPack;
import io.sapoj.dto.judger.ProblemSolutionDocument;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production import validator against every authored language/mode. */
class OjSolutionCorpusTest {
    @Test void everyEditorialMatchesItsActualProblemAndValidatedPrograms()throws Exception{
        var root=Path.of("../examples/original-sum");var json=new ObjectMapper();
        int documents=0,programs=0,diagrams=0;
        {
            var source=json.readTree(root.resolve("pack.json").toFile());String slug=source.path("slug").asText();
            var pack=json.treeToValue(source,ProblemPack.class);
            var file=root.resolve("solution.json");assertTrue(Files.exists(file),slug+" 题解缺失");
            var doc=json.readValue(file.toFile(),ProblemSolutionDocument.class);
            assertEquals(slug,doc.getProblemSlug());assertEquals(1L,doc.getProblemRevision());assertEquals(pack.getTitle(),doc.getTitle());
            assertDoesNotThrow(()->OjSolutionContent.validate(doc,pack),slug+" 无法导入");
            int length=doc.getSections().stream().mapToInt(s->s.getMarkdown().length()).sum();assertTrue(length>=500,slug+" 正文过短");
            for(var mode:doc.getCodes().entrySet())for(var program:mode.getValue().entrySet()){
                String code=program.getValue();long comments=code.lines().filter(line->line.stripLeading().startsWith(program.getKey().equals("python")?"#":"//")||line.stripLeading().startsWith("/*")).count();
                assertTrue(comments>=3,slug+" / "+mode.getKey()+" / "+program.getKey()+" 注释应分布在至少三个位置");programs++;
            }
            diagrams+=doc.getSections().stream().filter(s->s.getDiagram()!=null).count();documents++;
        }
        assertEquals(1,documents);assertEquals(5,programs);
    }
}
