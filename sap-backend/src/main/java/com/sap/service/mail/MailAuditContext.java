package com.sap.service.mail;

import com.sap.entity.User;
import java.util.*;

/** 日志专用业务快照，不复制模板参数、验证码或 HTML 邮件正文。 */
public final class MailAuditContext {
    private MailAuditContext() {}
    public static Map<String,Object> person(User user) { return person(user == null ? null : user.getId(), user); }
    public static Map<String,Object> person(Long id, User user) {
        var result = new LinkedHashMap<String,Object>();
        result.put("id", id);
        result.put("account", user == null ? "未记录" : Objects.toString(user.getStudentId(), "未记录"));
        result.put("name", user == null ? "未记录" : Objects.toString(user.getName(), "未记录"));
        return result;
    }
    public static Map<String,Object> event(MailHook hook, User recipient, Map<String,Object> initiator, String reason, Map<String,Object> details) {
        var result = new LinkedHashMap<String,Object>();
        result.put("source", "snapshot"); result.put("eventTitle", hook.title); result.put("reason", reason);
        result.put("recipient", person(recipient)); result.put("initiator", initiator); result.put("details", details);
        return result;
    }
    public static String excerpt(String text) {
        if (text == null || text.isBlank()) return "未记录";
        String clean = text.replaceAll("<[^>]*>", " ")
                .replaceAll("(?i)(密码|验证码|授权码|password|token|secret)\\s*[:：=]\\s*[^\\s，,；;]+", "$1：[已隐藏]");
        return clean.length() > 1000 ? clean.substring(0,1000) + "…（摘要已截断）" : clean;
    }
}
