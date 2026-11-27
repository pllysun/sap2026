package com.sap.mcp;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mcp-keys")
public class McpKeyController {
    private final McpKeyService service;

    @GetMapping
    public Result<McpKeyService.KeySettings> settings(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.ok(service.settings(StpUtil.getLoginIdAsLong()));
    }

    @PostMapping
    public Result<McpKeyService.CreatedKey> create(@Valid @RequestBody McpKeyService.CreateRequest request,
                                                   HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.ok(service.create(StpUtil.getLoginIdAsLong(), request));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable long id) {
        service.delete(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }
}
