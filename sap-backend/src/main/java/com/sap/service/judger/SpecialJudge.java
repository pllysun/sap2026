package com.sap.service.judger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Private checker code runs only in the existing Python sandbox, never in the application JVM. */
final class SpecialJudge {
    static final int MAX_DATA_LENGTH = 524288;
    private SpecialJudge() {}

    static String source(String checker) {
        // Only admin-authored source is executable. Candidate output is passed through framed stdin.
        String encoded=Base64.getEncoder().encodeToString(checker.getBytes(StandardCharsets.UTF_8));
        return """
            import base64, contextlib, sys
            class _Sink:
                def write(self, text): return len(text)
                def flush(self): pass
            try:
                stream = sys.stdin.buffer
                sizes = list(map(int, stream.readline().split()))
                if len(sizes) != 3 or any(n < 0 for n in sizes): raise ValueError()
                values = [stream.read(n).decode('utf-8') for n in sizes]
                namespace = {'__name__': '__checker__'}
                with contextlib.redirect_stdout(_Sink()):
                    exec(compile(base64.b64decode('%s'), '<checker>', 'exec'), namespace)
                    accepted = namespace['check'](*values)
                if type(accepted) is not bool: raise TypeError('check must return bool')
                print('AC' if accepted else 'WA')
            except BaseException:
                sys.exit(2)
            """.formatted(encoded);
    }

    static String input(String input,String actual,String expected) {
        // No JSON/base64 escaping amplification: retain the worker's existing 1 MiB stdin limit.
        if ((long)input.length()+expected.length()>MAX_DATA_LENGTH || actual.length()>262144)
            throw new IllegalArgumentException("Special judge input exceeds limit");
        return input.getBytes(StandardCharsets.UTF_8).length+" "+actual.getBytes(StandardCharsets.UTF_8).length+" "+
            expected.getBytes(StandardCharsets.UTF_8).length+"\n"+input+actual+expected;
    }
}
