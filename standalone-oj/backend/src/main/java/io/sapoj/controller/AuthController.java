package io.sapoj.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.sapoj.annotation.OperationLog;
import io.sapoj.common.BusinessException;
import io.sapoj.common.Result;
import io.sapoj.entity.User;
import io.sapoj.mapper.UserMapper;
import io.sapoj.service.Accounts;
import io.sapoj.service.AuthThrottle;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class AuthController {
    private final Accounts accounts;
    private final UserMapper users;
    private final AuthThrottle throttle;
    private final io.sapoj.service.BusinessSettings settings;
    public record Login(String studentId,String password) {}
    public record Create(String studentId,String password,String nickname,Integer roleCode) {}
    public record Edit(String nickname,Integer status,Integer roleCode) {}
    public record Password(String currentPassword,String password) {}
    private final Object[] authLocks=java.util.stream.IntStream.range(0,64).mapToObj(i->new Object()).toArray();
    private Object authLock(String account){return authLocks[Math.floorMod(account.hashCode(),authLocks.length)];}
    private User current(){return users.selectById(StpUtil.getLoginIdAsLong());}
    @GetMapping("/api/ping") public Result<?> ping(){return Result.ok("pong");}
    @GetMapping("/api/public/config") public Result<?> config(){return Result.ok(Map.of("siteName",settings.get().getSiteName(),"registrationEnabled",settings.get().getRegistrationEnabled()));}
    private Result<?> login(Login r,HttpServletRequest request,boolean admin){
        String account=accounts.account(r.studentId());
        throttle.take("login:"+request.getRemoteAddr(),60,60000);
        throttle.take("account:"+account,12,60000);
        synchronized(authLock(account)){
            var u=accounts.authenticate(r.studentId(),r.password(),admin);
            StpUtil.login(u.getId());return Result.ok(Map.of("token",StpUtil.getTokenValue(),"user",u,"roles",List.of(u.getRoleCode())));
        }
    }
    @PostMapping("/api/auth/login") public Result<?> login(@RequestBody Login r,HttpServletRequest request){return login(r,request,false);}
    @PostMapping("/api/auth/admin/login") public Result<?> adminLogin(@RequestBody Login r,HttpServletRequest request){return login(r,request,true);}
    @PostMapping("/api/auth/register") public Result<?> register(@RequestBody Create r,HttpServletRequest request){
        if(!settings.get().getRegistrationEnabled())throw new BusinessException(403,"当前未开放注册，请联系管理员创建账号");
        throttle.take("register:"+request.getRemoteAddr(),5,3600000);
        var u=accounts.create(r.studentId(),r.password(),r.nickname(),3);return Result.ok(Map.of("id",u.getId()));
    }
    @GetMapping("/api/auth/info") public Result<?> info(){var u=current();return Result.ok(Map.of("user",u,"roles",List.of(u.getRoleCode())));}
    @PostMapping("/api/auth/logout") public Result<?> logout(){StpUtil.logout();return Result.ok();}
    @PutMapping("/api/auth/profile") @OperationLog("修改个人昵称") public Result<?> profile(@RequestBody Edit r){
        if(r.nickname()==null||r.nickname().isBlank()||r.nickname().length()>50)throw new BusinessException(400,"昵称须为 1–50 个字符");
        var u=current();u.setNickname(r.nickname().strip());u.setName(r.nickname().strip());u.setUpdatedAt(LocalDateTime.now());users.updateById(u);return Result.ok(u);
    }
    @PutMapping("/api/auth/password") @OperationLog("修改个人密码") public Result<?> password(@RequestBody Password r){
        var u=current();synchronized(authLock(u.getStudentId())){u=current();if(!accounts.matches(r.currentPassword(),u))throw new BusinessException(400,"当前密码错误");accounts.reset(u,r.password());}return Result.ok();
    }
    @GetMapping("/api/admin/users") @SaCheckRole("0") public Result<?> users(@RequestParam(defaultValue="1") int page,@RequestParam(required=false) String keyword){
        var q=new LambdaQueryWrapper<User>().orderByAsc(User::getId);
        if(keyword!=null&&!keyword.isBlank())q.and(x->x.like(User::getStudentId,keyword).or().like(User::getNickname,keyword));
        return Result.ok(users.selectPage(new Page<>(Math.max(1,page),20),q));
    }
    @PostMapping("/api/admin/users") @SaCheckRole("0") @OperationLog("创建题库账号") public Result<?> create(@RequestBody Create r){
        int role=r.roleCode()==null?3:r.roleCode();if(!List.of(0,2,3).contains(role))throw new BusinessException(400,"权限类型无效");
        return Result.ok(accounts.create(r.studentId(),r.password(),r.nickname(),role));
    }
    @PutMapping("/api/admin/users/{id}") @SaCheckRole("0") @OperationLog("调整题库账号权限与状态") public Result<?> edit(@PathVariable Long id,@RequestBody Edit r){
        var u=users.selectById(id);if(u==null)throw new BusinessException(404,"账号不存在");
        if(r.status()==null||!List.of(0,1).contains(r.status())||r.roleCode()==null||!List.of(0,2,3).contains(r.roleCode()))throw new BusinessException(400,"账号配置无效");
        if(id.equals(StpUtil.getLoginIdAsLong())&&(r.status()!=1||r.roleCode()!=0))throw new BusinessException(400,"不能禁用当前管理账号或撤销自己的管理权限");
        if(u.getRoleCode()==0&&(r.roleCode()!=0||r.status()!=1)&&users.selectCount(new LambdaQueryWrapper<User>().eq(User::getRoleCode,0).eq(User::getStatus,1).ne(User::getId,id))==0)throw new BusinessException(400,"至少保留一个可用的超级管理员");
        if(r.nickname()==null||r.nickname().isBlank()||r.nickname().length()>50)throw new BusinessException(400,"昵称无效");
        u.setNickname(r.nickname().strip());u.setName(r.nickname().strip());u.setStatus(r.status());u.setRoleCode(r.roleCode());u.setUpdatedAt(LocalDateTime.now());users.updateById(u);
        if(r.status()!=1)StpUtil.kickout(id);return Result.ok(u);
    }
    @PutMapping("/api/admin/users/{id}/password") @SaCheckRole("0") @OperationLog("重置题库账号密码") public Result<?> reset(@PathVariable Long id,@RequestBody Password r){
        var u=users.selectById(id);if(u==null)throw new BusinessException(404,"账号不存在");synchronized(authLock(u.getStudentId())){accounts.reset(u,r.password());}return Result.ok();
    }
}
