package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.StudyMaterial;
import com.sap.mapper.StudyMaterialMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Network and COS work for administrator-authored Markdown homework. No database transaction wraps downloads. */
@Slf4j
@Service
public class HomeworkImageService {
    private static final int MAX_IMAGES = 30;
    private static final int MAX_MARKDOWN_BYTES = 4 * 1024 * 1024;
    private static final long MAX_DURATION_NANOS = TimeUnit.SECONDS.toNanos(90);
    private static final String IMAGE_CACHE = "public, max-age=31536000, immutable";

    @Autowired private CosService cosService;
    @Autowired private HomeworkImageFetcher fetcher;
    @Autowired private StudyMaterialMapper materialMapper;
    @Autowired private StudyService studyService;

    public record LocalizeResult(String fileUrl, int imagesCopied, int imagesFailed) {}

    /** Returns null fileUrl when no external image could be localized. */
    public LocalizeResult localizeAdminHomework(Long activityId, Integer week, String fileUrl, String fileName) {
        if (activityId == null || activityId < 1 || week == null || week < 1 || !isMarkdown(fileUrl, fileName)) {
            return new LocalizeResult(null, 0, 0);
        }
        byte[] original = cosService.readUploadObject(fileUrl, MAX_MARKDOWN_BYTES);
        String markdown = decode(original).replace("\r\n", "\n").replace('\r', '\n');
        long deadline = System.nanoTime() + MAX_DURATION_NANOS;
        Map<String, String> resolved = new HashMap<>();
        Map<String, String> byHash = new HashMap<>();
        int[] count = {0, 0, 0}; // encountered, copied, failed
        String rewritten = new HomeworkMarkdownRewriter().rewrite(markdown, url -> {
            if (isOwned(url)) return url;
            if (resolved.containsKey(url)) return resolved.get(url);
            if (++count[0] > MAX_IMAGES) {
                count[2]++;
                return url;
            }
            String updated = url;
            try {
                if (System.nanoTime() >= deadline) throw new IllegalStateException("Document time limit exceeded");
                HomeworkImageFetcher.ImageData image = fetcher.fetch(url, deadline);
                String hash = sha256(image.bytes());
                updated = byHash.get(hash);
                if (updated == null) {
                    if (System.nanoTime() >= deadline) throw new IllegalStateException("Document time limit exceeded");
                    updated = cosService.putObject(image.bytes(), "homework-images/" + hash + "." + image.extension(),
                            image.contentType(), IMAGE_CACHE);
                    byHash.put(hash, updated);
                    count[1]++;
                }
            } catch (Exception e) {
                count[2]++;
                log.warn("[HomeworkImage] Failed to copy image from host={}, reason={}", host(url), e.toString());
            }
            resolved.put(url, updated);
            return updated;
        });
        if (rewritten.equals(markdown)) return new LocalizeResult(null, count[1], count[2]);
        byte[] bytes = rewritten.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MARKDOWN_BYTES || System.nanoTime() >= deadline) {
            log.warn("[HomeworkImage] Rewritten Markdown exceeds size or time limit: activity={}, week={}", activityId, week);
            return new LocalizeResult(null, count[1], count[2]);
        }
        try {
            String key = "uploads/homework/" + activityId + "/" + week + "/" + sha256(bytes) + ".md";
            String newUrl = cosService.putObject(bytes, key, "text/markdown; charset=utf-8", null);
            return new LocalizeResult(newUrl, count[1], count[2]);
        } catch (Exception e) {
            log.warn("[HomeworkImage] Failed to store Markdown: activity={}, week={}, reason={}", activityId, week, e.toString());
            return new LocalizeResult(null, count[1], count[2]);
        }
    }

    /** Each row is independent. The conditional update cannot touch student submissions or a replaced homework. */
    public Map<String, Object> localizeExisting(Long activityId, Integer week) {
        if (activityId == null || activityId < 1 || week != null && week < 1) {
            throw new BusinessException("活动或周期不合法");
        }
        LambdaQueryWrapper<StudyMaterial> query = new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getActivityId, activityId).eq(StudyMaterial::getFileType, 1)
                .orderByAsc(StudyMaterial::getWeek);
        if (week != null) query.eq(StudyMaterial::getWeek, week);
        List<StudyMaterial> rows = materialMapper.selectList(query);
        List<Map<String, Object>> details = new ArrayList<>();
        int processed = 0, changed = 0, copied = 0, failed = 0;
        for (StudyMaterial row : rows) {
            if (!Integer.valueOf(1).equals(row.getFileType()) || !isMarkdown(row.getFileUrl(), row.getFileName())) continue;
            processed++;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("materialId", row.getId());
            detail.put("week", row.getWeek());
            try {
                LocalizeResult result = localizeAdminHomework(row.getActivityId(), row.getWeek(), row.getFileUrl(), row.getFileName());
                copied += result.imagesCopied();
                failed += result.imagesFailed();
                if (result.fileUrl() == null) {
                    detail.put("status", "unchanged");
                } else {
                    if (studyService.replaceAdminHomeworkFileUrl(row, result.fileUrl())) {
                        changed++;
                        detail.put("status", "changed");
                    } else {
                        detail.put("status", "stale");
                    }
                }
                detail.put("imagesCopied", result.imagesCopied());
                detail.put("imagesFailed", result.imagesFailed());
            } catch (Exception e) {
                detail.put("status", "failed");
                detail.put("reason", e.getClass().getSimpleName());
                log.warn("[HomeworkImage] Migration failed: material={}, reason={}", row.getId(), e.toString());
            }
            details.add(detail);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("processed", processed);
        response.put("changed", changed);
        response.put("imagesCopied", copied);
        response.put("imagesFailed", failed);
        response.put("details", details);
        return response;
    }

    private boolean isOwned(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && cosService.isOwnedPublicHost(uri.getHost());
        }
        catch (Exception e) { return false; }
    }

    private static boolean isMarkdown(String url, String name) {
        String candidate = name;
        if (candidate == null || candidate.isBlank()) {
            try { candidate = URI.create(url).getPath(); }
            catch (Exception e) { return false; }
        }
        if (candidate == null) return false;
        String lower = candidate.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    private static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 239 && (bytes[1] & 255) == 187 && (bytes[2] & 255) == 191)
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        if (bytes.length >= 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 254)
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        if (bytes.length >= 2 && (bytes[0] & 255) == 254 && (bytes[1] & 255) == 255)
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String host(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return "invalid"; }
    }
}
