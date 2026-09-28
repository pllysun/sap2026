package com.sap.service;

import com.sap.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理端成员档案：只读取明确以用户 ID 关联的业务数据，不查询日志、密码和教务凭据。 */
@Service
public class MemberProfileService {
    private final JdbcTemplate jdbc;

    public MemberProfileService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private record Section(String key, String label, String fields, String from, String order) {}

    // SQL 和字段均为服务端白名单；分页不会把大段正文或所有历史数据一次性加载进来。
    private static final List<Section> SECTIONS = List.of(
        new Section("terms", "任职档案",
            "t.id,t.grade,p.position_name AS positionName,t.created_at AS createdAt",
            "sys_term t LEFT JOIN sys_position p ON p.id=t.position_id WHERE t.deleted=0 AND t.user_id=?",
            "t.grade DESC,t.created_at DESC,t.id DESC"),
        new Section("joins", "入会申请",
            "j.id,j.status,m.name AS managerName,a.name AS approverName,j.created_at AS createdAt,j.submitted_at AS submittedAt,j.approved_at AS approvedAt",
            "join_application j LEFT JOIN sys_user m ON m.id=j.manager_id AND m.deleted=0 " +
            "LEFT JOIN sys_user a ON a.id=j.approved_by AND a.deleted=0 WHERE j.user_id=?",
            "j.created_at DESC,j.id DESC"),
        new Section("recruitment", "招新负责",
            "j.id,j.grade,j.created_at AS createdAt",
            "join_manager j WHERE j.user_id=?", "j.grade DESC,j.id DESC"),
        new Section("study", "学习小队",
            "m.id,a.title AS activityName,a.grade,m.week,u.name AS leaderName,s.score,s.comment,a.status,m.created_at AS createdAt",
            "study_member m JOIN study_activity a ON a.id=m.activity_id AND a.deleted=0 " +
            "LEFT JOIN study_leader l ON l.id=m.leader_id AND l.deleted=0 " +
            "LEFT JOIN sys_user u ON u.id=l.user_id AND u.deleted=0 " +
            "LEFT JOIN study_score s ON s.activity_id=m.activity_id AND s.week=m.week AND s.member_user_id=m.user_id " +
            "WHERE m.deleted=0 AND m.user_id=?", "m.created_at DESC,m.id DESC"),
        new Section("leading", "带队经历",
            "l.id,a.title AS activityName,a.grade,a.status,l.created_at AS createdAt," +
            "(SELECT COUNT(DISTINCT m.user_id) FROM study_member m WHERE m.leader_id=l.id AND m.deleted=0) AS memberCount",
            "study_leader l JOIN study_activity a ON a.id=l.activity_id AND a.deleted=0 WHERE l.deleted=0 AND l.user_id=?",
            "l.created_at DESC,l.id DESC"),
        new Section("materials", "作业与资料",
            "m.id,a.title AS activityName,m.week,m.file_type AS fileType,m.title,m.file_name AS fileName,m.created_at AS createdAt",
            "study_material m JOIN study_activity a ON a.id=m.activity_id AND a.deleted=0 WHERE m.user_id=?",
            "m.created_at DESC,m.id DESC"),
        new Section("reviews", "评分工作",
            "s.id,a.title AS activityName,u.name AS memberName,s.week,s.score,s.comment,s.created_at AS createdAt",
            "study_score s JOIN study_activity a ON a.id=s.activity_id AND a.deleted=0 " +
            "LEFT JOIN sys_user u ON u.id=s.member_user_id AND u.deleted=0 WHERE s.leader_user_id=?",
            "s.created_at DESC,s.id DESC"),
        new Section("notes", "发布笔记",
            "n.id,n.title,n.description,n.view_count AS viewCount,n.download_count AS downloadCount,n.created_at AS createdAt",
            "sap_note n WHERE n.deleted=0 AND n.author_id=?", "n.created_at DESC,n.id DESC"),
        new Section("messages", "留言与回复",
            "r.id,r.kind,SUBSTRING(r.content,1,2000) AS content,r.created_at AS createdAt",
            "(SELECT id,user_id,content,created_at,'留言' AS kind FROM msg_board WHERE deleted=0 UNION ALL " +
            "SELECT r.id,r.user_id,r.content,r.created_at,'回复' AS kind FROM msg_reply r " +
            "JOIN msg_board m ON m.id=r.message_id AND m.deleted=0 WHERE r.deleted=0) r WHERE r.user_id=?",
            "r.created_at DESC,r.id DESC,r.kind DESC"),
        new Section("likes", "互动点赞",
            "l.id,CASE WHEN l.target_type=0 THEN '留言' ELSE '回复' END AS kind," +
            "SUBSTRING(COALESCE(m.content,r.content),1,500) AS content,l.created_at AS createdAt",
            "msg_like l LEFT JOIN msg_board m ON l.target_type=0 AND m.id=l.target_id AND m.deleted=0 " +
            "LEFT JOIN msg_reply r ON l.target_type=1 AND r.id=l.target_id AND r.deleted=0 " +
            "WHERE l.user_id=? AND (m.id IS NOT NULL OR r.id IS NOT NULL)", "l.created_at DESC,l.id DESC"),
        new Section("feedback", "课表反馈",
            "i.id,i.title,SUBSTRING(i.content,1,2000) AS content,i.category,i.status,i.app_version_name AS appVersion,i.created_at AS createdAt,i.updated_at AS updatedAt",
            "app_feedback_issue i WHERE i.deleted=0 AND i.reporter_id=?", "i.created_at DESC,i.id DESC"),
        new Section("feedbackReplies", "反馈回复",
            "c.id,i.title,c.issue_id AS issueId,SUBSTRING(c.content,1,2000) AS content,c.admin_reply AS adminReply,c.created_at AS createdAt",
            "app_feedback_comment c JOIN app_feedback_issue i ON i.id=c.issue_id AND i.deleted=0 WHERE c.deleted=0 AND c.author_id=?",
            "c.created_at DESC,c.id DESC")
    );

    public Map<String, Object> profile(Long userId) {
        Map<String, Object> user = findUser(userId);
        var result = new LinkedHashMap<String, Object>();
        result.put("user", user);
        result.put("roles", jdbc.queryForList("SELECT role_code FROM sys_user_role WHERE user_id=? ORDER BY role_code", Integer.class, userId));
        // 不把注册时间或首次导入任职档案的时间冒充真实入会时间。
        result.put("joinedAt", jdbc.queryForObject("SELECT MIN(approved_at) FROM join_application WHERE user_id=? AND status=2", java.sql.Timestamp.class, userId));
        result.put("firstArchivedAt", jdbc.queryForObject("SELECT MIN(created_at) FROM sys_term WHERE user_id=? AND deleted=0", java.sql.Timestamp.class, userId));
        result.put("sections", SECTIONS.stream().map(section -> Map.<String, Object>of(
            "key", section.key, "label", section.label, "total", count(section, userId))).toList());
        return result;
    }

    public Map<String, Object> relations(Long userId, String key, int current, int size) {
        Section section = SECTIONS.stream().filter(item -> item.key.equals(key)).findFirst()
            .orElseThrow(() -> new BusinessException(400, "不支持的成员资料分类"));
        findUser(userId);
        current = Math.max(1, current);
        size = Math.max(1, Math.min(50, size));
        long total = count(section, userId);
        long offset = (long) (current - 1) * size;
        List<Map<String, Object>> records = offset >= total ? List.of() : jdbc.queryForList(
            "SELECT " + section.fields + " FROM " + section.from + " ORDER BY " + section.order + " LIMIT ? OFFSET ?", userId, size, offset);
        return Map.of("records", records, "total", total, "current", current, "size", size);
    }

    private long count(Section section, Long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + section.from, Long.class, userId);
        return count == null ? 0 : count;
    }

    private Map<String, Object> findUser(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException(400, "无效的成员编号");
        var rows = jdbc.queryForList("SELECT id,student_id AS studentId,name,nickname,gender,qq,grade,avatar,status," +
            "created_at AS createdAt,updated_at AS updatedAt FROM sys_user WHERE id=? AND deleted=0", userId);
        if (rows.isEmpty()) throw new BusinessException(404, "成员不存在或已删除");
        return rows.getFirst();
    }
}
