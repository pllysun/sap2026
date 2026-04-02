package com.sap.controller;

import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.CosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private CosService cosService;

    @PostMapping("/upload")
    @OperationLog("上传文件")
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        Map<String, String> result = cosService.upload(file);
        return Result.ok(result);
    }

    @PostMapping("/upload/batch")
    @OperationLog("批量上传文件")
    public Result<?> batchUpload(@RequestParam("files") MultipartFile[] files) {
        List<Map<String, String>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                results.add(cosService.upload(file));
            }
        }
        return Result.ok(results);
    }

    /**
     * 代理下载 – 解决 COS 跨域下载文件名为 UUID 的问题
     */
    @GetMapping("/download")
    public void download(@RequestParam String url,
                         @RequestParam(required = false, defaultValue = "file") String name,
                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            java.net.URL fileUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) fileUrl.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            String contentType = conn.getContentType();
            if (contentType == null) contentType = "application/octet-stream";

            String encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20");
            response.setContentType(contentType);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);

            try (java.io.InputStream in = conn.getInputStream();
                 java.io.OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
        } catch (Exception e) {
            response.setStatus(500);
        }
    }
}
