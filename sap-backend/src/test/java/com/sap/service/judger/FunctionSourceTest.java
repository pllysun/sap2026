package com.sap.service.judger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FunctionSourceTest {
    @Test void cAndCppExposeStandardLibrariesBeforeDriverAndKeepCode() {
        String c = FunctionSource.assemble("c", "__USER_CODE__\nint main(void){return 0;}", "int f(void){return abs(-1);}");
        assertTrue(c.indexOf("#include <stdlib.h>") < c.indexOf("int f"));
        assertTrue(c.contains("#include <math.h>"));assertFalse(c.contains("__USER_CODE__"));
        String cpp = FunctionSource.assemble("cpp", "__USER_CODE__", "vector<int> f(){return {1};}");
        assertTrue(cpp.contains("#include <bits/stdc++.h>"));assertTrue(cpp.contains("using namespace std;"));
    }
    @Test void javaPreludeProvidesCollectionsIoMathAndStreams() {
        String code = "import java.util.HashMap;\nclass Solution {}";
        String assembled = FunctionSource.assemble("java", "__USER_CODE__\nclass Main {}", code);
        assertTrue(assembled.startsWith("import java.util.*;"));assertTrue(assembled.contains("import java.math.*;"));
        assertTrue(assembled.contains(code));assertTrue(assembled.contains("import java.util.stream.*;"));
    }
    @Test void rustPreludePreservesCrateAttributesExplicitImportsAndReservedNames() {
        String code = "#![allow(dead_code)]\nuse std::collections::HashMap;\nmod __sap_core_prelude {}\nfn main() {}";
        String source = FunctionSource.assemble("rust", "__USER_CODE__", code);
        assertTrue(source.startsWith(code));assertTrue(source.contains("mod __sap_core_prelude_ {"));
        assertTrue(source.contains("use __sap_core_prelude_::*;"));assertTrue(source.contains("BinaryHeap, VecDeque"));
    }
    @Test void pythonPreparationPreservesDocstringAndFutureImportsWithoutExecutingCode() {
        String prepare = FunctionSource.pythonCompiler(true);
        assertTrue(prepare.contains("tree.body[pos:pos] = prefix.body"));
        assertTrue(prepare.contains("tree.body[pos].module == '__future__'"));
        assertTrue(prepare.contains("compile(tree, 'main.py', 'exec')"));
        assertFalse(prepare.contains("exec("));assertFalse(prepare.contains("eval("));
        assertEquals("from __future__ import annotations\ndef f(): pass\n", FunctionSource.assemble("python", "__USER_CODE__\n", "from __future__ import annotations\ndef f(): pass"));
    }
    @Test void fullProgramPreparationKeepsExistingPythonCommand() {
        assertEquals("import ast; ast.parse(open('main.py',encoding='utf-8').read(),filename='main.py')", FunctionSource.pythonCompiler(false));
    }
}
