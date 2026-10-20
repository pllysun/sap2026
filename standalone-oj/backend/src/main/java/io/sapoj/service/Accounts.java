package io.sapoj.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.sapoj.common.BusinessException;
import io.sapoj.entity.User;
import io.sapoj.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class Accounts implements ApplicationRunner {
    private final UserMapper users;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    @Value("${app.bootstrap.admin-account:}") private String bootstrapAccount;
    @Value("${app.bootstrap.admin-password:}") private String bootstrapPassword;
    public String account(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{2,31}")) throw new BusinessException(400,"账号须为 3–32 位字母、数字、点、下划线或短横线");
        return value.toLowerCase(Locale.ROOT);
    }
    public User byAccount(String value) { return users.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentId,account(value))); }
    public void password(String value) {
        if (value == null || value.length() < 12 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(400,"密码须至少 12 个字符，UTF-8 长度不超过 72 字节");
    }
    public User create(String account, String password, String nickname, int role) {
        String normalized = account(account); password(password);
        if (byAccount(normalized) != null) throw new BusinessException(409,"账号已存在");
        if (nickname == null || nickname.isBlank() || nickname.length() > 50) throw new BusinessException(400,"昵称须为 1–50 个字符");
        User u = new User(); u.setStudentId(normalized); u.setPassword(passwords.encode(password));
        u.setName(nickname.strip()); u.setNickname(nickname.strip()); u.setRoleCode(role);
        u.setStatus(1); u.setDeleted(0); u.setCreatedAt(LocalDateTime.now()); u.setUpdatedAt(LocalDateTime.now());
        users.insert(u); return u;
    }
    public User authenticate(String account, String password, boolean admin) {
        User u;
        try { u=byAccount(account); } catch (BusinessException e) { throw new BusinessException(400,"账号或密码错误"); }
        if (u == null || password == null || password.length() > 128 || !passwords.matches(password,u.getPassword()) || !Integer.valueOf(1).equals(u.getStatus()) || admin && u.getRoleCode() >= 3)
            throw new BusinessException(400,"账号或密码错误");
        return u;
    }
    public void reset(User u, String password) {
        password(password); u.setPassword(passwords.encode(password)); u.setUpdatedAt(LocalDateTime.now()); users.updateById(u); StpUtil.kickout(u.getId());
    }
    public boolean matches(String plain, User u) { return plain != null && passwords.matches(plain,u.getPassword()); }
    @Override public void run(ApplicationArguments arguments) {
        if (bootstrapPassword != null && bootstrapPassword.toLowerCase(java.util.Locale.ROOT).startsWith("replace-")) throw new IllegalStateException("请运行 tools/init.py 生成私有密码，不能使用示例占位值");
        if (users.selectCount(null)==0 && (bootstrapAccount.isBlank() || bootstrapPassword.isBlank()))
            throw new IllegalStateException("首次启动必须配置 OJ_ADMIN_ACCOUNT 和 OJ_ADMIN_PASSWORD；运行 tools/init.py 生成私有配置");
        if (!bootstrapAccount.isBlank() && !bootstrapPassword.isBlank() && byAccount(bootstrapAccount)==null)
            create(bootstrapAccount,bootstrapPassword,"管理员",0);
    }
}
