package io.sapoj.controller;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import io.sapoj.annotation.OperationLog;
import io.sapoj.common.Result;
import io.sapoj.entity.SiteSettings;
import io.sapoj.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class SettingsController {
    private final BusinessSettings settings;
    private final ProblemArchives archives;
    @GetMapping("/api/admin/settings") @SaCheckRole("0") public Result<?> get(){return Result.ok(settings.get());}
    @PutMapping("/api/admin/settings") @SaCheckRole("0") @OperationLog("更新题库业务设置") public Result<?> save(@RequestBody SiteSettings row){return Result.ok(settings.save(row));}
    @GetMapping("/api/admin/storage") @SaCheckRole(value={"0","2"},mode=SaMode.OR) public Result<?> storage(){return Result.ok(archives.status());}
    @PostMapping("/api/admin/storage/check") @SaCheckRole(value={"0","2"},mode=SaMode.OR) public Result<?> check(){return Result.ok(archives.check());}
    @PostMapping("/api/admin/storage/problems/{id}/archive") @SaCheckRole(value={"0","2"},mode=SaMode.OR) @OperationLog("归档私有题包到对象存储") public Result<?> archive(@PathVariable Long id){return Result.ok(archives.archive(id));}
    @GetMapping("/api/admin/storage/archives") @SaCheckRole(value={"0","2"},mode=SaMode.OR) public Result<?> list(){return Result.ok(archives.list());}
    @GetMapping("/api/admin/storage/archives/{id}") @SaCheckRole(value={"0","2"},mode=SaMode.OR) @OperationLog("下载私有题包归档") public ResponseEntity<byte[]> read(@PathVariable String id){
        return ResponseEntity.ok().header("Content-Type","application/json; charset=utf-8").header("Content-Disposition","attachment; filename=\""+id+".json\"")
            .header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").body(archives.read(id));
    }
}
