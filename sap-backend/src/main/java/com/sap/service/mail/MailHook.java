package com.sap.service.mail;

import com.sap.common.BusinessException;
import java.util.List;
import java.util.Map;

/** 事件及参数由业务代码定义，模板只能使用该契约内的变量。 */
public enum MailHook {
    ACCOUNT_REGISTERED("注册成功", "account.registered", List.of("name", "associationIntro")),
    APP_REGISTRATION_CODE("App 注册邮箱验证码", "app.registration-code", List.of("name", "account", "email", "code", "expiresInMinutes")),
    PASSWORD_CODE("修改密码验证码", "account.password-code", List.of("name", "account", "code", "expiresInMinutes")),
    MEMBER_JOINED("升级会员 / 欢迎入会", "member.upgraded", List.of("name")),
    ISSUE_REPLIED("App 问题管理员回复", "app.issue-replied", List.of("name", "issueId", "issueTitle", "issueStatus", "adminName", "replyContent", "repliedAt")),
    STUDY_REVIEW_REQUESTED("学习小队待评分提醒", "study.review-reminder", List.of("adminName", "teamName", "taskTitle", "pendingCount", "taskDeadline", "remindedAt", "reviewUrl"));

    public final String title;
    public final String initialTemplateKey;
    public final List<String> variables;
    MailHook(String title, String key, List<String> variables) {
        this.title = title; this.initialTemplateKey = key; this.variables = variables;
    }
    /** 编辑器说明直接从代码契约生成，不受已保存模板或绑定影响。示例不是真实业务数据。 */
    public record Parameter(String name, String label, String description, String example) {}
    public Map<String,Object> definition() {
        return Map.of("eventKey",name(), "title",title, "variables",variables,
                "parameters",variables.stream().map(MailHook::parameter).toList(),
                "guidance",switch(this) {
                    case APP_REGISTRATION_CODE -> "手机端注册时验证 QQ 邮箱。code 和 expiresInMinutes 必须放在正文，主题不得含验证码；验证码由注册业务生成、限时且一次有效。注册成功后继续触发注册成功事件。";
                    case PASSWORD_CODE -> "验证码 code 和有效分钟数 expiresInMinutes 必须放在正文，主题不得含验证码。示例只用于预览；用户点击发送后，找回密码流程向注册 QQ 邮箱发送真实验证码，实际有效期由密码业务控制。";
                    case ISSUE_REPLIED -> "请引导用户打开软协课表 App，在「我的 → 意见反馈」中查看对应问题与回复，不要添加不存在的问题链接参数。";
                    case ACCOUNT_REGISTERED -> "欢迎用户了解软件协会；associationIntro 由管理员在事件绑定时填写，其他参数由业务提供。";
                    case MEMBER_JOINED -> "欢迎加入软件协会、共同成长与进步，无需展示会员等级等信息。";
                    case STUDY_REVIEW_REQUESTED -> "提醒学习小队负责人处理待评分任务，reviewUrl 由业务提供。";
                });
    }
    private static Parameter parameter(String name) {
        return switch(name) {
            case "name" -> new Parameter(name,"收件人姓名","用户资料中的姓名", "示例同学");
            case "associationIntro" -> new Parameter(name,"协会介绍","事件绑定中配置的欢迎介绍", "欢迎了解软件协会，一起交流技术、参与实践。");
            case "account" -> new Parameter(name,"账号","发起验证操作的学号", "20260001");
            case "email" -> new Parameter(name,"QQ 邮箱","本次注册 QQ 号对应的邮箱", "123456789@qq.com");
            case "code" -> new Parameter(name,"验证码","验证业务生成的验证码，仅允许放在正文", "123456");
            case "expiresInMinutes" -> new Parameter(name,"有效分钟数","验证码真实有效时长（分钟），不能在模板中写死", "5");
            case "issueId" -> new Parameter(name,"问题编号","反馈问题的编号", "128");
            case "issueTitle" -> new Parameter(name,"问题标题","用户提交的问题标题", "课表显示问题");
            case "issueStatus" -> new Parameter(name,"问题状态","回复时的问题状态", "处理中");
            case "adminName" -> new Parameter(name,"管理员姓名","回复问题的管理员或待评分负责人姓名", "示例管理员");
            case "replyContent" -> new Parameter(name,"回复内容","管理员的回复正文", "已收到反馈，正在核实。");
            case "repliedAt" -> new Parameter(name,"回复时间","管理员回复的时间", "2026-09-06 10:00");
            case "teamName" -> new Parameter(name,"学习小队","需要处理评分的小队名称", "软件开发小队");
            case "taskTitle" -> new Parameter(name,"任务标题","待评分的学习任务", "第一周练习");
            case "pendingCount" -> new Parameter(name,"待评分数量","等待负责人评分的提交数量", "3");
            case "taskDeadline" -> new Parameter(name,"任务截止时间","当前任务的截止时间", "2026-09-10 23:59");
            case "remindedAt" -> new Parameter(name,"提醒时间","本次业务提醒触发时间", "2026-09-06 10:00");
            case "reviewUrl" -> new Parameter(name,"评分入口","业务提供的评分页面链接", "https://example.com/review");
            default -> throw new IllegalArgumentException("缺少邮件参数说明：" + name);
        };
    }
    public static MailHook parse(String value) {
        try { return valueOf(value); }
        catch (Exception e) { throw new BusinessException(400, "未知邮件代码事件"); }
    }
    public boolean isVerificationCode() { return this == PASSWORD_CODE || this == APP_REGISTRATION_CODE; }
    public static boolean isVerificationCode(String key) {
        return PASSWORD_CODE.name().equals(key) || APP_REGISTRATION_CODE.name().equals(key);
    }
}
