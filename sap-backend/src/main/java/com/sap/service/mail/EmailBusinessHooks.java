package com.sap.service.mail;

import com.sap.entity.*;
import com.sap.mapper.UserMapper;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 业务入口只知道事件及参数，不知道模板标识或 HTML。收件地址来自用户资料 QQ。 */
@Service
public class EmailBusinessHooks {
    private final MailQueue queue;
    private final MailStore store;
    private final UserMapper users;
    public EmailBusinessHooks(MailQueue queue, MailStore store, UserMapper users) { this.queue=queue;this.store=store;this.users=users; }
    public void registered(User user) {
        send(MailHook.ACCOUNT_REGISTERED,user,Map.of("name",name(user)),"user:"+user.getId(),null,
                MailAuditContext.event(MailHook.ACCOUNT_REGISTERED,user,MailAuditContext.person(user),"账号注册成功，发送欢迎了解软件协会邮件",Map.of("注册账号",text(user.getStudentId()),"注册姓名",name(user))));
    }
    /** 用户尚未注册，不查询既有用户邮箱；收件地址严格从本次待验证 QQ 号生成。 */
    public String appRegistrationCode(String account,String name,String qq,String code,Instant expiresAt,String requestId) {
        if(code==null || !code.matches("[0-9]{6}") || expiresAt==null || !expiresAt.isAfter(Instant.now()))
            throw new com.sap.common.BusinessException(400,"验证码必须为六位数字并设置有效期");
        User pending=new User(); pending.setStudentId(account); pending.setName(name); pending.setQq(qq);
        long minutes=Math.max(1,(Duration.between(Instant.now(),expiresAt).toSeconds()+59)/60);
        return send(MailHook.APP_REGISTRATION_CODE,pending,
                Map.of("name",name,"account",account,"email",qq+"@qq.com","code",code,"expiresInMinutes",minutes),requestId,expiresAt.toEpochMilli(),
                MailAuditContext.event(MailHook.APP_REGISTRATION_CODE,pending,MailAuditContext.person(pending),
                        "手机端申请注册，验证本次 QQ 邮箱归属",Map.of("用途","App 注册邮箱验证（验证码不记入日志）","有效期至",expiresAt.toString())));
    }
    public void memberJoined(Long userId) {
        User user=users.selectById(userId);
        if(user!=null)send(MailHook.MEMBER_JOINED,user,Map.of("name",name(user)),"user:"+userId,null,
                MailAuditContext.event(MailHook.MEMBER_JOINED,user,currentActor(),"用户升级为协会会员，发送欢迎加入协会邮件",Map.of("会员账号",text(user.getStudentId()),"会员姓名",name(user))));
    }
    /** 找回密码邮件入口：密码业务负责生成/校验验证码和真实有效期。 */
    public String passwordCode(User user,String code,Instant expiresAt,String requestId) {
        if(code==null||!code.matches("\\d{6}")||expiresAt==null||!expiresAt.isAfter(Instant.now()))
            throw new com.sap.common.BusinessException(400,"验证码必须为六位数字并设置有效期");
        long minutes=Math.max(1,(Duration.between(Instant.now(),expiresAt).toSeconds()+59)/60);
        return send(MailHook.PASSWORD_CODE,user,Map.of("name",name(user),"account",user.getStudentId(),"code",code,"expiresInMinutes",minutes),requestId,expiresAt.toEpochMilli(),
                MailAuditContext.event(MailHook.PASSWORD_CODE,user,MailAuditContext.person(user),"用户申请修改密码，发送身份验证邮件",Map.of("用途","修改密码验证（验证码不记入日志）","有效期至",expiresAt.toString())));
    }
    public void issueReplied(AppFeedbackIssue issue,AppFeedbackComment comment,User admin) {
        if(!Boolean.TRUE.equals(comment.getAdminReply())||Objects.equals(comment.getAuthorId(),issue.getReporterId()))return;
        User user=users.selectById(issue.getReporterId());if(user==null)return;
        send(MailHook.ISSUE_REPLIED,user,Map.of("name",name(user),"issueId",issue.getId(),"issueTitle",issue.getTitle(),
                "issueStatus","CLOSED".equals(issue.getStatus())?"已关闭":"处理中","adminName",name(admin),"replyContent",comment.getContent(),"repliedAt",time()),"comment:"+comment.getId(),null,
                MailAuditContext.event(MailHook.ISSUE_REPLIED,user,MailAuditContext.person(comment.getAuthorId(),admin==null?users.selectById(comment.getAuthorId()):admin),
                        "管理员回复问题反馈，通知问题提交人跟进",
                        Map.of("问题编号",issue.getId(),"问题标题",MailAuditContext.excerpt(issue.getTitle()),"问题内容摘要",MailAuditContext.excerpt(issue.getContent()),
                                "回复编号",comment.getId(),"回复摘要",MailAuditContext.excerpt(comment.getContent()),"问题状态","CLOSED".equals(issue.getStatus())?"已关闭":"处理中")));
    }
    /** 新提交触发待评分提醒；同一负责人、活动和周期每天最多一封，避免多名学员提交刷屏。 */
    public void studySubmission(Long activityId,Integer week,Long studentId) {
        var leaders=store.db.queryForList("SELECT l.user_id FROM study_member m JOIN study_leader l ON m.leader_id=l.id WHERE m.activity_id=? AND m.week=? AND m.user_id=? AND m.deleted=0 AND l.deleted=0",activityId,week,studentId);
        for(var row:leaders) {
            Long leaderId=((Number)row.get("user_id")).longValue();User leader=users.selectById(leaderId);if(leader==null)continue;
            Long pending=store.db.queryForObject("SELECT COUNT(DISTINCT m.user_id) FROM study_member m JOIN study_leader l ON m.leader_id=l.id WHERE m.activity_id=? AND m.week=? AND m.deleted=0 AND l.deleted=0 AND l.user_id=? AND EXISTS (SELECT 1 FROM study_material s WHERE s.activity_id=m.activity_id AND s.week=m.week AND s.user_id=m.user_id AND s.file_type=2) AND NOT EXISTS (SELECT 1 FROM study_score sc WHERE sc.activity_id=m.activity_id AND sc.week=m.week AND sc.member_user_id=m.user_id)",Long.class,activityId,week,leaderId);
            if(pending==null||pending==0)continue;
            var activities=store.db.queryForList("SELECT title,grade,seq_num FROM study_activity WHERE id=?",activityId);if(activities.isEmpty())continue;
            var activity=activities.get(0);String title=String.valueOf(activity.get("title"));
            send(MailHook.STUDY_REVIEW_REQUESTED,leader,Map.of("adminName",name(leader),"teamName",activity.get("grade")+" 级学习小队", "taskTitle",title+" · 第 "+week+" 周期", "pendingCount",pending,"taskDeadline","以任务页面公布时间为准","remindedAt",time(),"reviewUrl","https://csuftsap.top/study"),
                    activityId+":"+week+":"+leaderId+":"+LocalDate.now(ZoneId.of("Asia/Shanghai")),null,
                    MailAuditContext.event(MailHook.STUDY_REVIEW_REQUESTED,leader,MailAuditContext.person(studentId,users.selectById(studentId)),
                            "学员提交学习任务，提醒对应负责人评分",Map.of("活动编号",activityId,"学习小队",activity.get("grade")+" 级学习小队","任务",title,"周期",week,"待评分人数",pending)));
        }
    }
    private String send(MailHook hook,User user,Map<String,Object> values,String id,Long expiry,Map<String,Object> context) {
        if(user==null)return null;
        String qq=user.getQq();
        if(qq==null||!qq.matches("[1-9][0-9]{4,14}")) {
            store.audit(null,hook.name(),null,null,"SKIPPED","用户资料没有有效 QQ 邮箱",MailQueue.initiatorId(context),context);return null;
        }
        return queue.enqueue(hook,user.getId(),qq+"@qq.com",values,id,expiry,context);
    }
    private Map<String,Object> currentActor() {
        try {
            Object id=cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull();
            if(id!=null) {Long userId=Long.valueOf(String.valueOf(id));return MailAuditContext.person(userId,users.selectById(userId));}
        } catch(cn.dev33.satoken.exception.SaTokenContextException ignored) { }
        return Map.of("name","系统自动处理","account","系统");
    }
    private String text(String value){return value==null||value.isBlank()?"未记录":value;}
    private String name(User user){return user==null?"协会管理员":Objects.toString(user.getName(),"同学");}
    private String time(){return ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}
}
