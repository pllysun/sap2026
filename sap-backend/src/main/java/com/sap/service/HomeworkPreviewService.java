package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.StudyActivity;
import com.sap.entity.StudyHomeworkDoc;
import com.sap.entity.StudyMaterial;
import com.sap.entity.StudyMember;
import com.sap.mapper.StudyActivityMapper;
import com.sap.mapper.StudyHomeworkDocMapper;
import com.sap.mapper.StudyMaterialMapper;
import com.sap.mapper.StudyMemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作业在线预览。
 * <p>Markdown 原文仍以 COS 为源；第一次预览时读取一次并写入 {@code study_homework_doc}，
 * 之后直接读库。替换/删除作业时由 {@link StudyService} 调用 {@link #evict} 清缓存。</p>
 */
@Slf4j
@Service
public class HomeworkPreviewService {

    /** 作业正文上限：超过这个大小就降级为下载，避免把大文件塞进 DB / JSON。 */
    public static final int MAX_PREVIEW_BYTES = 512 * 1024;

    private static final Charset GB18030 = Charset.forName("GB18030");

    @Autowired private StudyActivityMapper studyActivityMapper;
    @Autowired private StudyMaterialMapper studyMaterialMapper;
    @Autowired private StudyMemberMapper studyMemberMapper;
    @Autowired private StudyHomeworkDocMapper studyHomeworkDocMapper;
    @Autowired private CosService cosService;

    /**
     * 预览某个活动/周期的作业。
     *
     * @param admin 管理员（role 0-2）可以预览未发布的排期作业
     */
    public Map<String, Object> preview(Long activityId, Integer week, Long userId, boolean admin) {
        if (activityId == null || week == null || week < 1) {
            throw new BusinessException(400, "参数不合法");
        }
        StudyActivity activity = studyActivityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException(404, "学习活动不存在");

        StudyMaterial homework = studyMaterialMapper.selectOne(
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getActivityId, activityId)
                        .eq(StudyMaterial::getWeek, week)
                        .eq(StudyMaterial::getFileType, 1));
        if (homework == null) throw new BusinessException(404, "该周期没有布置作业");

        if (!admin) {
            if (!Integer.valueOf(1).equals(activity.getStatus())) {
                throw new BusinessException(403, "学习活动已关闭");
            }
            if (homework.getPublishTime() != null && homework.getPublishTime().isAfter(LocalDateTime.now())) {
                throw new BusinessException(403, "作业尚未发布");
            }
            Long joined = studyMemberMapper.selectCount(
                    new LambdaQueryWrapper<StudyMember>()
                            .eq(StudyMember::getActivityId, activityId)
                            .eq(StudyMember::getUserId, userId));
            if (joined == null || joined == 0) {
                throw new BusinessException(403, "请先加入学习小组");
            }
        }

        Map<String, Object> result = baseInfo(homework, activityId, week);
        String format = detectFormat(homework.getFileName(), homework.getFileUrl());
        if (format == null) {
            result.put("previewable", false);
            result.put("reason", "该文件暂不支持在线预览，请下载查看");
            return result;
        }

        StudyHomeworkDoc doc = findDoc(homework.getId());
        if (doc == null) {
            try {
                byte[] bytes = cosService.readUploadObject(homework.getFileUrl(), MAX_PREVIEW_BYTES);
                String content = decode(bytes);
                if (content == null || content.isBlank()) {
                    result.put("previewable", false);
                    result.put("reason", "作业正文为空，请下载查看");
                    return result;
                }
                doc = saveDoc(homework, format, content, bytes.length);
            } catch (Exception e) {
                log.warn("[HomeworkPreview] 生成失败 activity={}, week={}, file={}, error={}",
                        activityId, week, homework.getFileName(), e.getMessage());
                result.put("previewable", false);
                result.put("reason", "在线预览生成失败，请下载查看");
                return result;
            }
        }

        result.put("previewable", true);
        result.put("format", doc.getContentFormat());
        result.put("content", doc.getContent());
        result.put("sourceBytes", doc.getSourceBytes());
        result.put("updatedAt", doc.getUpdatedAt() != null ? doc.getUpdatedAt() : doc.getFetchedAt());
        return result;
    }

    /** 替换/删除作业时清掉正文缓存，下一次预览会重新从 COS 读取。 */
    public void evict(Long activityId, Integer week) {
        if (activityId == null || week == null) return;
        studyHomeworkDocMapper.delete(new LambdaQueryWrapper<StudyHomeworkDoc>()
                .eq(StudyHomeworkDoc::getActivityId, activityId)
                .eq(StudyHomeworkDoc::getWeek, week));
    }

    /**
     * 按 materialId 查缓存。不能按 activity+week 查：替换作业会换 materialId，
     * 旧请求只能写入旧 materialId 的行，不会污染当前周期。
     */
    private StudyHomeworkDoc findDoc(Long materialId) {
        if (materialId == null) return null;
        return studyHomeworkDocMapper.selectOne(new LambdaQueryWrapper<StudyHomeworkDoc>()
                .eq(StudyHomeworkDoc::getMaterialId, materialId));
    }

    private StudyHomeworkDoc saveDoc(StudyMaterial homework, String format,
                                     String content, int sourceBytes) {
        StudyHomeworkDoc doc = new StudyHomeworkDoc();
        doc.setMaterialId(homework.getId());
        doc.setActivityId(homework.getActivityId());
        doc.setWeek(homework.getWeek());
        doc.setContentFormat(format);
        doc.setContent(content);
        doc.setSourceBytes(sourceBytes);
        doc.setFetchedAt(LocalDateTime.now());
        try {
            studyHomeworkDocMapper.insert(doc);
            return doc;
        } catch (DuplicateKeyException e) {
            // 同一份作业的并发首次预览：唯一键是 materialId，读回先写入的那份即可
            StudyHomeworkDoc existing = findDoc(homework.getId());
            if (existing != null) return existing;
            throw e;
        }
    }

    private Map<String, Object> baseInfo(StudyMaterial homework, Long activityId, Integer week) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityId", activityId);
        result.put("week", week);
        result.put("title", homework.getTitle() != null ? homework.getTitle() : homework.getFileName());
        result.put("fileName", homework.getFileName());
        result.put("fileUrl", homework.getFileUrl());
        return result;
    }

    /** 目前只支持 Markdown；文件名缺失时退回 URL 路径判断。 */
    private String detectFormat(String fileName, String fileUrl) {
        String name = fileName;
        if (name == null || name.isBlank()) {
            try {
                String path = java.net.URI.create(fileUrl).getPath();
                name = path != null ? path.substring(path.lastIndexOf('/') + 1) : null;
            } catch (Exception ignore) {
                name = null;
            }
        }
        if (name == null) return null;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return (lower.endsWith(".md") || lower.endsWith(".markdown")) ? "markdown" : null;
    }

    /** UTF-8 优先，失败退 GB18030；兼容 UTF-8/UTF-16 BOM。 */
    private String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, GB18030);
        }
    }
}
