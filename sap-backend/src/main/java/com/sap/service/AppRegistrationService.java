package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.dto.AppRegisterDTO;
import com.sap.service.mail.*;
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

/** 手机端邮箱验证：持久化限额与 HMAC 摘要，创建账号和消费验证码在同一事务内。 */
@Service
public class AppRegistrationService {
    public static final int COOLDOWN_SECONDS = 180;
    public static final long TTL = 15 * 60_000L;
    private static final String INVALID = "邮箱验证码错误、已过期或已使用，请核对学号和 QQ 邮箱后重试";
    private final MailStore store;
    private final EmailBusinessHooks hooks;
    private final EmailService email;
    private final CaptchaService captcha;
    private final AuthService auth;
    private final RegistrationProtectionService protection;
    private final SecureRandom random = new SecureRandom();
    @Value("${jw.aes-key}") private String key;

    public AppRegistrationService(MailStore store, EmailBusinessHooks hooks, EmailService email,
                                  CaptchaService captcha, AuthService auth, RegistrationProtectionService protection) {
        this.store=store; this.hooks=hooks; this.email=email; this.captcha=captcha; this.auth=auth; this.protection=protection;
    }

    @PostConstruct public void init() {
        store.db.execute("CREATE TABLE IF NOT EXISTS sys_app_registration_code (id VARCHAR(64) PRIMARY KEY, account_hash VARCHAR(64) NOT NULL, qq_hash VARCHAR(64) NOT NULL, ip_hash VARCHAR(64) NOT NULL, code_hash VARCHAR(64), created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, attempts INT NOT NULL DEFAULT 0, consumed INT NOT NULL DEFAULT 0, INDEX idx_app_reg_account(account_hash,created_at), INDEX idx_app_reg_qq(qq_hash,created_at), INDEX idx_app_reg_ip(ip_hash,created_at), INDEX idx_app_reg_time(created_at))");
    }
    long now() { return System.currentTimeMillis(); }
    private String digest(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("注册邮箱服务配置异常",e); }
    }
    private String accountHash(String account) { return digest("app-register:account:"+account.trim().toLowerCase(Locale.ROOT)); }
    private String qqHash(String qq) { return digest("app-register:qq:"+qq); }
    private long count(String condition,Object... args) {
        return store.db.queryForObject("SELECT COUNT(*) FROM sys_app_registration_code WHERE "+condition,Long.class,args);
    }
    private void available() {
        var rows=store.db.queryForList("SELECT template_id FROM sys_mail_binding WHERE event_key='APP_REGISTRATION_CODE'");
        try {
            if(!email.sendingEnabled() || rows.isEmpty() || rows.get(0).get("template_id")==null) throw new IllegalStateException();
            var template=email.getTemplate(((Number)rows.get(0).get("template_id")).longValue());
            if(!Boolean.TRUE.equals(template.get("enabled"))) throw new IllegalStateException();
            MailQueue.validateContract(MailHook.APP_REGISTRATION_CODE,template);
        } catch(RuntimeException e) { throw new BusinessException(503,"注册验证码邮件暂不可用，请稍后再试或联系管理员"); }
        if(store.db.queryForObject("SELECT COUNT(*) FROM sys_mail_queue",Long.class)>=5)
            throw new BusinessException(429,"邮件发送较忙，请几分钟后再试");
    }
    public Map<String,Object> request(String account,String name,String qq,String captchaId,String answer,String ip) {
        if(account==null || !account.matches("[A-Za-z0-9_-]{1,20}") || qq==null || !qq.matches("[1-9][0-9]{4,14}")
                || name==null || name.isBlank() || name.length()>50) throw new BusinessException(400,"请填写正确的学号、姓名和 QQ 号");
        // 发送邮件始终先验证图形挑战，避免匿名接口被用于批量发信；不改变 Web 注册配置。
        if(captchaId==null || captchaId.isBlank() || answer==null || answer.isBlank()) return Map.of("captchaRequired",true);
        if(!captcha.verify(captchaId,answer,ip)) throw new BusinessException(400,"图形验证码错误或已过期，请刷新后重试");
        return store.transaction(() -> {
            store.lock(); available(); long now=now(),day=now-86_400_000L;
            String ah=accountHash(account),qh=qqHash(qq),ih=digest("app-register:ip:"+ip);
            if(count("(account_hash=? OR qq_hash=?) AND created_at>?",ah,qh,now-COOLDOWN_SECONDS*1000L)>0)
                throw new BusinessException(429,"验证码申请过于频繁，请间隔 3 分钟后重试");
            if(count("account_hash=? AND created_at>?",ah,day)>=5 || count("qq_hash=? AND created_at>?",qh,day)>=5
                    || count("ip_hash=? AND created_at>?",ih,now-3_600_000L)>=10 || count("ip_hash=? AND created_at>?",ih,day)>=30
                    || count("created_at>?",now-3_600_000L)>=30 || count("created_at>?",day)>=200)
                throw new BusinessException(429,"注册验证码申请次数已达上限，请稍后再试");
            String id=UUID.randomUUID().toString(),code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
            store.db.update("INSERT INTO sys_app_registration_code(id,account_hash,qq_hash,ip_hash,code_hash,created_at,expires_at) VALUES(?,?,?,?,?,?,?)",
                    id,ah,qh,ih,digest(id+":"+code),now,now+TTL);
            if(hooks.appRegistrationCode(account,name,qq,code,Instant.ofEpochMilli(now+TTL),"app-registration:"+id)==null)
                throw new BusinessException(503,"注册验证码邮件暂无法生成，请稍后重试");
            store.db.update("UPDATE sys_app_registration_code SET consumed=1,code_hash=NULL WHERE (account_hash=? OR qq_hash=?) AND id<>?",ah,qh,id);
            return Map.of("requestId",id,"email",qq+"@qq.com","cooldownSeconds",COOLDOWN_SECONDS,"expiresInSeconds",TTL/1000,
                    "notice","验证码邮件已加入发送队列，请查收 QQ 邮箱及垃圾箱，15 分钟内有效");
        });
    }
    public void register(AppRegisterDTO dto,String ip) {
        String id=dto.getEmailRequestId(),code=dto.getEmailCode();
        if(id==null || !id.matches("[0-9a-f-]{36}") || code==null || !code.matches("[0-9]{6}")
                || dto.getStudentId()==null || dto.getQq()==null) throw new BusinessException(400,INVALID);
        boolean registered=store.transaction(() -> {
            store.lock();
            var rows=store.db.queryForList("SELECT * FROM sys_app_registration_code WHERE id=? FOR UPDATE",id);
            if(rows.isEmpty()) return false;
            var row=rows.get(0);
            if(((Number)row.get("consumed")).intValue()!=0 || ((Number)row.get("expires_at")).longValue()<=now()
                    || ((Number)row.get("attempts")).intValue()>=5) return false;
            store.db.update("UPDATE sys_app_registration_code SET attempts=attempts+1 WHERE id=?",id);
            String expected=Objects.toString(row.get("code_hash"),"");
            if(!Objects.equals(accountHash(dto.getStudentId()),row.get("account_hash")) || !Objects.equals(qqHash(dto.getQq()),row.get("qq_hash"))
                    || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),digest(id+":"+code).getBytes(StandardCharsets.UTF_8))) return false;
            try(var permit=protection.acquire(ip,dto.getQq())) {
                auth.register(dto); // 包含既有 ACCOUNT_REGISTERED 成功钩子；事务失败不会消耗正确验证码。
                store.db.update("UPDATE sys_app_registration_code SET consumed=1,code_hash=NULL WHERE account_hash=? OR qq_hash=?",row.get("account_hash"),row.get("qq_hash"));
            }
            return true;
        });
        // 验证失败在事务提交之后抛出，错误次数不能随异常回滚。
        if(!registered) throw new BusinessException(400,INVALID);
    }
    @Scheduled(cron="0 40 4 * * *",zone="Asia/Shanghai") public void cleanup() {
        store.db.update("DELETE FROM sys_app_registration_code WHERE created_at<?",now()-2*86_400_000L);
    }
}
