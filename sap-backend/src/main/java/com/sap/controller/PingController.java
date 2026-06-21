package com.sap.controller;

import com.sap.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康探针：App 进场/切回前台时用它快速判断后端是否可达，决定「在线/离线」模式。
 * 极轻量、免登录、不碰 DB——只为连通性探测。
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }
}
