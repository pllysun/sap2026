package com.sap.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomeworkMarkdownRewriterTest {
    private final HomeworkMarkdownRewriter rewriter = new HomeworkMarkdownRewriter();

    @Test
    void rewritesOnlyImageDestinationsAndPreservesFormatting() {
        String source = "# Homework ![head](https://old.example/h.png)\n\n"
                + "![inline](https://old.example/a.png \"a title\") ![angle](<https://old.example/b.png>)\n"
                + "`![code](https://old.example/code.png)`\n\n"
                + "```md\n![fenced](https://old.example/fenced.png)\n```\n\n"
                + "<img alt='ok' data-src='https://old.example/lazy.png' src='https://old.example/html.png'>\n\n"
                + "![again](https://old.example/a.png) ![ref][id]\n\n[id]: https://old.example/ref.png\n";
        String updated = rewriter.rewrite(source, url -> url.replace("old.example", "dl.csuftsap.top"));
        assertTrue(updated.contains("# Homework ![head](https://dl.csuftsap.top/h.png)"));
        assertTrue(updated.contains("![inline](https://dl.csuftsap.top/a.png \"a title\")"));
        assertTrue(updated.contains("![angle](<https://dl.csuftsap.top/b.png>)"));
        assertTrue(updated.contains("src='https://dl.csuftsap.top/html.png'"));
        assertTrue(updated.contains("data-src='https://old.example/lazy.png'"));
        assertTrue(updated.contains("![again](https://dl.csuftsap.top/a.png)"));
        assertTrue(updated.contains("`![code](https://old.example/code.png)`"));
        assertTrue(updated.contains("![fenced](https://old.example/fenced.png)"));
        assertTrue(updated.contains("![ref][id]\n\n[id]: https://old.example/ref.png"));
    }

    @Test
    void unchangedWhenCallbackSkipsOwnedUrl() {
        String source = "![already](https://dl.csuftsap.top/homework-images/a.png)";
        assertEquals(source, rewriter.rewrite(source, url -> url));
    }
}
