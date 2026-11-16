package com.sap.service;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rewrites only image destinations identified by the Markdown AST. */
@Slf4j
final class HomeworkMarkdownRewriter {
    private static final Pattern IMG_TAG = Pattern.compile("<img\\b(?:\"[^\"]*\"|'[^']*'|[^'\">])*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SRC = Pattern.compile("(?:^|\\s)src\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.CASE_INSENSITIVE);
    private record Replacement(int start, int end, String value) {}

    String rewrite(String source, Function<String, String> localize) {
        Node document = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build().parse(source);
        List<Replacement> replacements = new ArrayList<>();
        visit(document, source, localize, replacements);
        replacements.sort(Comparator.comparingInt(Replacement::start).reversed());
        StringBuilder result = new StringBuilder(source);
        int previousStart = source.length() + 1;
        for (Replacement replacement : replacements) {
            if (replacement.end() > previousStart) continue;
            result.replace(replacement.start(), replacement.end(), replacement.value());
            previousStart = replacement.start();
        }
        return result.toString();
    }

    private void visit(Node node, String source, Function<String, String> localize, List<Replacement> replacements) {
        if (node instanceof Image image) {
            int[] range = range(source, node.getSourceSpans());
            if (range != null) {
                String raw = source.substring(range[0], range[1]);
                int opener = raw.lastIndexOf("](");
                if (opener < 0) {
                    log.info("[HomeworkImage] Reference-style image skipped");
                } else {
                    int pos = opener + 2;
                    while (pos < raw.length() && Character.isWhitespace(raw.charAt(pos))) pos++;
                    if (pos < raw.length() && raw.charAt(pos) == '<') pos++;
                    String destination = image.getDestination();
                    if (raw.startsWith(destination, pos)) {
                        add(localize, replacements, destination, range[0] + pos, range[0] + pos + destination.length());
                    } else {
                        log.info("[HomeworkImage] Image destination without literal source span skipped");
                    }
                }
            }
        } else if (node instanceof HtmlInline || node instanceof HtmlBlock) {
            int[] range = range(source, node.getSourceSpans());
            if (range != null) {
                String raw = source.substring(range[0], range[1]);
                if (!raw.startsWith("<!--")) {
                    Matcher tags = IMG_TAG.matcher(raw);
                    while (tags.find()) {
                        Matcher src = SRC.matcher(tags.group());
                        if (!src.find()) continue;
                        int group = src.group(1) != null ? 1 : src.group(2) != null ? 2 : 3;
                        String literal = src.group(group);
                        String destination = org.jsoup.parser.Parser.unescapeEntities(literal, true);
                        add(localize, replacements, destination,
                                range[0] + tags.start() + src.start(group),
                                range[0] + tags.start() + src.end(group));
                    }
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            visit(child, source, localize, replacements);
        }
    }

    private void add(Function<String, String> localize, List<Replacement> replacements,
                     String destination, int start, int end) {
        String updated = localize.apply(destination);
        if (updated != null && !updated.equals(destination)) replacements.add(new Replacement(start, end, updated));
    }

    private int[] range(String source, List<SourceSpan> spans) {
        if (spans.isEmpty()) return null;
        int start = offset(source, spans.getFirst());
        SourceSpan last = spans.getLast();
        int end = offset(source, last) + last.getLength();
        return start >= 0 && end <= source.length() && start < end ? new int[]{start, end} : null;
    }

    private int offset(String source, SourceSpan span) {
        int pos = 0;
        for (int line = 0; line < span.getLineIndex(); line++) {
            pos = source.indexOf('\n', pos);
            if (pos < 0) return -1;
            pos++;
        }
        return pos + span.getColumnIndex();
    }
}
