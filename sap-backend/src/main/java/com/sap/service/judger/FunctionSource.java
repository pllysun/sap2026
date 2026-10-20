package com.sap.service.judger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Standard-library prelude for FUNCTION submissions; STDIO remains self-contained. */
public final class FunctionSource {
    private FunctionSource() {}
    private static final String C = """
        #include <stdio.h>
        #include <stdlib.h>
        #include <string.h>
        #include <math.h>
        #include <stdbool.h>
        #include <stdint.h>
        #include <stddef.h>
        #include <limits.h>
        #include <float.h>
        #include <ctype.h>
        #include <inttypes.h>
        #include <assert.h>
        """;
    private static final String CPP = "#include <bits/stdc++.h>\nusing namespace std;\n";
    private static final String JAVA = """
        import java.util.*;
        import java.io.*;
        import java.math.*;
        import java.time.*;
        import java.util.function.*;
        import java.util.stream.*;
        """;
    private static final String PYTHON = """
        import sys, math, collections, itertools, functools, heapq, bisect, statistics, operator, decimal, fractions, re, copy, typing
        from math import floor, ceil, sqrt, gcd, lcm, isqrt, inf, pi, e
        from collections import Counter, defaultdict, deque, OrderedDict
        from itertools import permutations, combinations, product, accumulate, chain, repeat, groupby
        from functools import lru_cache, cache, reduce, cmp_to_key
        from heapq import heapify, heappush, heappop, heappushpop, heapreplace, nlargest, nsmallest
        from bisect import bisect_left, bisect_right, insort_left, insort_right
        from typing import List, Optional, Dict, Set, Tuple, Deque, Any, Iterable, Iterator
        from decimal import Decimal
        from fractions import Fraction
        from copy import deepcopy
        """;
    public static String assemble(String language, String driver, String code) {
        String source = driver.replace("__USER_CODE__", code);
        return switch (language) {
            case "c" -> C + source;
            case "cpp" -> CPP + source;
            case "java" -> JAVA + source;
            // Imports are inserted by the trusted AST preparation command after future imports.
            case "python" -> source;
            case "rust" -> rust(source);
            default -> throw new IllegalArgumentException("Unsupported function language");
        };
    }
    private static String rust(String source) {
        String name = "__sap_core_prelude";
        while (source.contains(name)) name += "_";
        // A glob prelude allows existing explicit imports and local definitions to win.
        // Suffix placement keeps crate-level inner attributes at the start of the file.
        return source + "\n#[allow(unused_imports)]\nmod " + name + " {\n" + """
            pub use std::collections::{HashMap, HashSet, BTreeMap, BTreeSet, BinaryHeap, VecDeque, LinkedList};
            pub use std::cmp::{min, max, Ordering, Reverse};
            pub use std::io::{self, Read, Write, BufRead};
            pub use std::iter::{once, repeat};
            pub use std::mem::{swap, replace, take};
            """ + "}\n#[allow(unused_imports)]\nuse " + name + "::*;\n";
    }
    public static String pythonCompiler(boolean function) {
        if (!function) return "import ast; ast.parse(open('main.py',encoding='utf-8').read(),filename='main.py')";
        String imports = Base64.getEncoder().encodeToString(PYTHON.getBytes(StandardCharsets.UTF_8));
        // Parse/compile only: submitted code is never executed during source preparation.
        return """
            import ast, base64
            from pathlib import Path
            path = Path('main.py')
            tree = ast.parse(path.read_text(encoding='utf-8'), filename='main.py')
            compile(tree, 'main.py', 'exec')
            pos = 0
            if tree.body and isinstance(tree.body[0], ast.Expr) and isinstance(tree.body[0].value, ast.Constant) and isinstance(tree.body[0].value.value, str):
                pos = 1
            while pos < len(tree.body) and isinstance(tree.body[pos], ast.ImportFrom) and tree.body[pos].module == '__future__':
                pos += 1
            prefix = ast.parse(base64.b64decode('%s').decode('utf-8'))
            tree.body[pos:pos] = prefix.body
            ast.fix_missing_locations(tree)
            Path('answer').write_text(ast.unparse(tree) + '\\n', encoding='utf-8')
            """.formatted(imports);
    }
}
