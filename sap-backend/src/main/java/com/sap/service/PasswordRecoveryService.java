package com.sap.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.User;
import com.sap.mapper.UserMapper;
import com.sap.service.mail.*;
import com.sap.util.PasswordUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** 找回密码与邮件入队同事务；限额落库，重启不清零。验证码仅保存带服务端密钥的摘要。 */
@Service
public class PasswordRecoveryService {
    public static final int COOLDOWN_SECONDS = 180;
    public static final long TTL = 15 * 60_000L;
    public static final String REQUEST_NOTICE = "如账号存在且注册 QQ 有效，验证码将发送到该 QQ 邮箱。邮件按队列发送，请耐心等待并检查垃圾箱；QQ 填错、收不到邮件或密码仍不正确，请联系管理员。";
    private static final String INVALID = "验证码无效、已过期或尝试次数过多，请重新申请；QQ 邮箱不正确或仍无法找回，请联系管理员";
    private final MailStore store;
    private final UserMapper users;
    private final EmailBusinessHooks hooks;
    private final EmailService email;
    private final CacheService cache;
    private final CaptchaService captcha;
    private final AuthService auth;
    private final SecureRandom random = new SecureRandom();
    @Value("${jw.aes-key}") private String key;

    public PasswordRecoveryService(MailStore store, UserMapper users, EmailBusinessHooks hooks,
                                   EmailService email, CacheService cache, CaptchaService captcha, AuthService auth) {
        this.store=store; this.users=users; this.hooks=hooks; this.email=email; this.cache=cache; this.captcha=captcha; this.auth=auth;
    }
    @PostConstruct public void init() {
        store.db.execute("CREATE TABLE IF NOT EXISTS sys_password_recovery (id VARCHAR(64) PRIMARY KEY, user_id BIGINT, account_hash VARCHAR(64) NOT NULL, qq_hash VARCHAR(64), ip_hash VARCHAR(64) NOT NULL, code_hash VARCHAR(64), password_stamp VARCHAR(64), created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, attempts INT NOT NULL DEFAULT 0, consumed INT NOT NULL DEFAULT 0, INDEX idx_recovery_account(account_hash,created_at), INDEX idx_recovery_qq(qq_hash,created_at), INDEX idx_recovery_ip(ip_hash,created_at), INDEX idx_recovery_time(created_at))");
    }
    long now() { return System.currentTimeMillis(); }
    private String digest(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("找回密码服务配置异常",e); }
    }
    private long count(String condition,Object... args) {
        return store.db.queryForObject("SELECT COUNT(*) FROM sys_password_recovery WHERE "+condition,Long.class,args);
    }
    private void limit(boolean exceeded) {
        if(exceeded) throw new BusinessException(429,"申请过于频繁，请至少间隔 3 分钟；每个账号/QQ 邮箱 24 小时最多 3 次，仍有问题请联系管理员");
    }
    private void available() {
        var bindings=store.db.queryForList("SELECT template_id FROM sys_mail_binding WHERE event_key='PASSWORD_CODE'");
        try {
            if(!email.sendingEnabled() || bindings.isEmpty() || bindings.get(0).get("template_id")==null) throw new IllegalStateException();
            var template=email.getTemplate(((Number)bindings.get(0).get("template_id")).longValue());
            if(!Boolean.TRUE.equals(template.get("enabled"))) throw new IllegalStateException();
            MailQueue.validateContract(MailHook.PASSWORD_CODE,template);
        } catch(RuntimeException e) { throw new BusinessException(503,"找回密码邮件暂不可用，请联系管理员"); }
        // 不插队或绕过 65 秒间隔；积压时拒绝新申请，避免验证码尚未发出就已过期。
        if(store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue",Long.class)>=5)
            throw new BusinessException(429,"邮件队列较忙，请几分钟后再试，或联系管理员");
    }
    public Map<String,Object> request(String account,String captchaId,String answer,String ip) {
        if(account==null || !account.trim().matches("[A-Za-z0-9_-]{1,20}")) throw new BusinessException(400,"请输入正确的注册账号（学号）");
        if(!captcha.verify(captchaId,answer,ip)) throw new BusinessException(400,"图形验证码不正确或已过期，请刷新后重试");
        String normalized=account.trim().toLowerCase(Locale.ROOT);
        return store.transaction(() -> {
            store.lock(); available(); long now=now(),day=now-86_400_000L;
            String ah=digest("account:"+normalized),ih=digest("ip:"+ip);
            limit(count("account_hash=? AND created_at>?",ah,now-COOLDOWN_SECONDS*1000L)>0
                    ||count("account_hash=? AND created_at>?",ah,day)>=3);
            limit(count("ip_hash=? AND created_at>?",ih,now-3_600_000L)>=5 ||count("ip_hash=? AND created_at>?",ih,day)>=10);
            // 小项目全局硬上限，无法通过换账号/IP无限发信。
            if(count("created_at>?",now-3_600_000L)>=8 ||count("created_at>?",day)>=30)
                throw new BusinessException(429,"今日或本小时找回申请较多，请稍后再试，紧急情况请联系管理员");
            User user=users.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentId,normalized));
            String qq=user==null ? null:user.getQq();
            boolean eligible=user!=null && Integer.valueOf(1).equals(user.getStatus()) && qq!=null && qq.matches("[1-9][0-9]{4,14}");
            String qh=eligible?digest("qq:"+qq):null;
            if(eligible)limit(count("qq_hash=? AND created_at>?",qh,day)>=3 ||count("qq_hash=? AND created_at>?",qh,now-COOLDOWN_SECONDS*1000L)>0);
            String id=UUID.randomUUID().toString();
            String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
            // 不存在的账号也记录同样的申请和随机凭据，不暴露账号/QQ 是否存在。
            store.db.update("INSERT INTO sys_password_recovery(id,user_id,account_hash,qq_hash,ip_hash,code_hash,password_stamp,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    id,eligible?user.getId():null,ah,qh,ih,eligible?digest(id+":"+code):null,eligible?digest("password:"+user.getPassword()):null,now,now+TTL);
            if(eligible && hooks.passwordCode(user,code,Instant.ofEpochMilli(now+TTL),"recovery:"+id)==null)
                throw new BusinessException(503,"验证码邮件暂无法生成，请联系管理员");
            store.db.update("UPDATE sys_password_recovery SET consumed=1,code_hash=NULL WHERE account_hash=? AND id<>?",ah,id);
            return Map.of("requestId",id,"cooldownSeconds",COOLDOWN_SECONDS,"expiresInSeconds",TTL/1000,"notice",REQUEST_NOTICE);
        });
    }
    public void reset(String id,String code,String password) {
        if(id==null||!id.matches("[0-9a-f-]{36}")||code==null||!code.matches("[0-9]{6}"))throw new BusinessException(400,INVALID);
        if(password==null||password.length()<6||password.length()>64 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new BusinessException(400,"新密码需为 6–64 位，UTF-8 长度不超过 72 字节");
        // 错误次数必须提交，不能因业务异常回滚；密码哈希只在验证码已通过后计算。
        User changed=store.transaction(() -> {
            store.lock();
            var rows=store.db.queryForList("SELECT * FROM sys_password_recovery WHERE id=? FOR UPDATE",id);
            if(rows.isEmpty())return null;
            var row=rows.get(0);
            if(((Number)row.get("consumed")).intValue()!=0 || ((Number)row.get("expires_at")).longValue()<=now() || ((Number)row.get("attempts")).intValue()>=5)return null;
            store.db.update("UPDATE sys_password_recovery SET attempts=attempts+1 WHERE id=?",id);
            String expected=Objects.toString(row.get("code_hash"),"");
            if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),digest(id+":"+code).getBytes(StandardCharsets.UTF_8)))return null;
            User user=users.selectById(((Number)row.get("user_id")).longValue());
            if(user==null || !Integer.valueOf(1).equals(user.getStatus()) || !Objects.equals(digest("qq:"+user.getQq()),row.get("qq_hash"))
                    || !Objects.equals(digest("password:"+user.getPassword()),row.get("password_stamp")))return null;
            String encoded=PasswordUtil.encode(password);
            int updated=store.db.update("UPDATE sys_user SET password=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND password=? AND qq=? AND deleted=0 AND status=1",encoded,user.getId(),user.getPassword(),user.getQq());
            if(updated!=1)return null;
            store.db.update("UPDATE sys_password_recovery SET consumed=1,code_hash=NULL WHERE user_id=?",user.getId());
            // 注销该账号全部设备。即使事务随后失败，旧会话被注销也不会获得额外权限。
            invalidateSessions(user.getId());
            user.setPassword(encoded);
            store.audit(null,MailHook.PASSWORD_CODE.name(),user.getQq()+"@qq.com",null,"PASSWORD_RESET","邮箱验证通过，密码已重置，旧登录已注销",user.getId(),
                    MailAuditContext.event(MailHook.PASSWORD_CODE,user,MailAuditContext.person(user),"用户通过注册 QQ 邮箱验证码找回密码",Map.of("结果","重置成功（密码与验证码不记入日志）")));
            return user;
        });
        if(changed==null)throw new BusinessException(400,INVALID);
        cache.updateUser(changed);
        auth.clearPasswordRecoveryLock(changed.getStudentId());
    }
    void invalidateSessions(Long userId) { StpUtil.logout(userId); }
    @Scheduled(cron="0 30 4 * * *",zone="Asia/Shanghai") public void cleanup() {
        store.db.update("DELETE FROM sys_password_recovery WHERE created_at<?",now()-2*86_400_000L);
    }
}
